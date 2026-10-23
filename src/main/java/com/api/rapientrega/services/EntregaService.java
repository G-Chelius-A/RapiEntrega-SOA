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

        // [ALGORITMO GEOGRÁFICO DE ASIGNACIÓN ÓPTIMA]:
        // Ordenamos los repartidores activos evaluando la distancia ortodrómica esférica (Haversine)
        // desde la posición GPS del repartidor hacia las coordenadas fijas del Restaurante.
        disponibles.sort(Comparator.comparingDouble(r ->
                calcularDistanciaHaversineKm(restLat, restLon, r.getLatitud(), r.getLongitud())));

        // [CONTROL DE CONCURRENCIA / CONDICIONAL ATÓMICO]:
        // En escenarios de alta demanda, dos clientes pueden solicitar pedidos en el mismo segundo.
        // Si ambos intentan tomar al mismo repartidor más cercano, ocurriría una "Condición de Carrera" (Race Condition).
        // updateDisponibilidadCondicional ejecuta en la BD un UPDATE atómico: 
        // "UPDATE repartidor SET disponible = false WHERE id = :id AND disponible = true"
        // Si devuelve 1 fila afectada, ganamos la reserva; si devuelve 0, otro hilo ya lo tomó y pasamos al siguiente candidato.
        Repartidor repartidorAsignado = null;
        for (Repartidor candidato : disponibles) {
            int filasAfectadas = repartidorRepository.updateDisponibilidadCondicional(candidato.getId(), true, false);
            if (filasAfectadas > 0) {
                // Se reservó con éxito sin necesidad de bloqueos pesados de tabla
                candidato.setDisponible(false);
                repartidorAsignado = candidato;
                break;
            }
        }

        if (repartidorAsignado == null) {
            log.warn("Conflicto concurrente al reservar repartidor para pedido {}. Todos los candidatos fueron tomados.", pedidoId);
            return pedido;
        }

        // Asociar repartidor al pedido y transicionar a ASIGNADO
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
     * [FÓRMULA TRIGONOMÉTRICA DE HAVERSINE]:
     * Calcula la distancia del gran círculo (distancia ortodrómica) entre dos puntos en una esfera
     * a partir de sus longitudes y latitudes (modelando la curvatura de la Tierra con radio promedio de 6,371 km).
     * 
     * ¿Por qué no usar el teorema de Pitágoras plano (dx^2 + dy^2)?
     * Porque la Tierra no es plana: un grado de longitud mide ~111 km en el ecuador, pero se reduce a 0 en los polos.
     * Haversine convierte grados a radianes y calcula el semiverseno del ángulo central (a),
     * garantizando precisión métrica para aplicaciones de delivery en distancias cortas y medias.
     */
    public double calcularDistanciaHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        // Diferencial de latitud y longitud en radianes
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2);

        // c = 2 * atan2( √a, √(1−a) ) -> Ángulo central subtendido
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        // Distancia = Radio * Ángulo central
        return RADIO_TIERRA_KM * c;
    }
}
