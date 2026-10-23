package com.api.rapientrega.services;

import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.models.Repartidor;
import com.api.rapientrega.models.Restaurante;
import com.api.rapientrega.repositories.PedidoRepository;
import com.api.rapientrega.repositories.RepartidorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class EntregaService {

    private static final Logger log = LoggerFactory.getLogger(EntregaService.class);

    private static final double RADIO_TIERRA_KM = 6371.0;

    private final RepartidorRepository repartidorRepository;
    private final PedidoRepository pedidoRepository;
    private final RepartidorService repartidorService;

    public EntregaService(RepartidorRepository repartidorRepository,
                          PedidoRepository pedidoRepository,
                          RepartidorService repartidorService) {
        this.repartidorRepository = repartidorRepository;
        this.pedidoRepository = pedidoRepository;
        this.repartidorService = repartidorService;
    }

    /**
     * Asigna el repartidor disponible más cercano al restaurante del pedido.
     * Utiliza bloqueo optimista mediante updateDisponibilidadCondicional para evitar
     * condiciones de carrera si dos pedidos intentan reservar al mismo repartidor.
     */
    @Transactional
    public Pedido asignarRepartidorMasCercano(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!PedidoService.ESTADO_ACEPTADO.equalsIgnoreCase(pedido.getEstado()) &&
            !PedidoService.ESTADO_EN_PREPARACION.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("Solo se puede asignar repartidor a pedidos aceptados o en preparación. Estado actual: " + pedido.getEstado());
        }

        Restaurante restaurante = pedido.getRestaurante();
        Double restLat = restaurante.getLatitud();
        Double restLon = restaurante.getLongitud();

        List<Repartidor> disponibles = repartidorRepository.findByDisponible(true);

        if (disponibles.isEmpty()) {
            log.warn("No hay repartidores disponibles para asignar al pedido {}. Queda en estado {}", pedidoId, pedido.getEstado());
            return pedido;
        }

        // Ordenar por distancia usando fórmula de Haversine
        disponibles.sort(Comparator.comparingDouble(r ->
                calcularDistanciaHaversineKm(restLat, restLon, r.getLatitud(), r.getLongitud())));

        // Intentar reservar atómicamente el repartidor más cercano
        Repartidor repartidorAsignado = null;
        for (Repartidor candidato : disponibles) {
            int filasAfectadas = repartidorRepository.updateDisponibilidadCondicional(candidato.getId(), true, false);
            if (filasAfectadas > 0) {
                // Se reservó con éxito
                candidato.setDisponible(false);
                repartidorAsignado = candidato;
                break;
            }
        }

        if (repartidorAsignado == null) {
            log.warn("Conflicto concurrente al reservar repartidor para pedido {}. Todos los candidatos fueron tomados.", pedidoId);
            return pedido;
        }

        // Asociar repartidor al pedido y transicionar a ASIGNADO o EN_PREPARACION
        pedido.setRepartidor(repartidorAsignado);
        pedido.setEstado(PedidoService.ESTADO_ASIGNADO);
        Pedido pedidoActualizado = pedidoRepository.save(pedido);

        log.info("Repartidor {} asignado con éxito al pedido {}", repartidorAsignado.getId(), pedidoId);
        return pedidoActualizado;
    }

    @Transactional
    public Pedido iniciarEntrega(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!PedidoService.ESTADO_ASIGNADO.equalsIgnoreCase(pedido.getEstado()) &&
            !PedidoService.ESTADO_EN_PREPARACION.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("El pedido no está listo para iniciar entrega. Estado actual: " + pedido.getEstado());
        }

        if (pedido.getRepartidor() == null) {
            throw new BusinessRuleException("No se puede iniciar entrega sin un repartidor asignado");
        }

        pedido.setEstado(PedidoService.ESTADO_EN_CAMINO);
        return pedidoRepository.save(pedido);
    }

    @Transactional
    public Pedido completarEntrega(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!PedidoService.ESTADO_EN_CAMINO.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("Solo se puede completar la entrega de un pedido en camino. Estado actual: " + pedido.getEstado());
        }

        pedido.setEstado(PedidoService.ESTADO_ENTREGADO);
        pedido.setFechaEntrega(LocalDateTime.now());
        Pedido guardado = pedidoRepository.save(pedido);

        // Liberar al repartidor para que vuelva a estar disponible
        if (pedido.getRepartidor() != null) {
            Long repId = pedido.getRepartidor().getId();
            repartidorRepository.updateDisponibilidadCondicional(repId, false, true);
            log.info("Repartidor {} liberado exitosamente tras completar pedido {}", repId, pedidoId);
        }

        return guardado;
    }

    @Transactional
    public Repartidor actualizarUbicacionRepartidor(Long repartidorId, Double latitud, Double longitud) {
        return repartidorService.actualizarUbicacion(repartidorId, latitud, longitud);
    }

    @Transactional(readOnly = true)
    public Repartidor consultarUbicacionRepartidor(Long repartidorId) {
        return repartidorService.consultarPorId(repartidorId);
    }

    @Transactional(readOnly = true)
    public Repartidor consultarSeguimientoPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));
        if (pedido.getRepartidor() == null) {
            throw new BusinessRuleException("El pedido aún no tiene repartidor asignado para seguimiento");
        }
        return repartidorRepository.findById(pedido.getRepartidor().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Repartidor asociado no encontrado"));
    }

    /**
     * Cálculo de distancia entre dos coordenadas geográficas mediante la fórmula de Haversine.
     * Retorna la distancia en kilómetros.
     */
    public double calcularDistanciaHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return RADIO_TIERRA_KM * c;
    }
}
