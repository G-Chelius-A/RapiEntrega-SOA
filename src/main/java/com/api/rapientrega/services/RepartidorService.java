package com.api.rapientrega.services;

import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.models.Repartidor;
import com.api.rapientrega.repositories.PedidoRepository;
import com.api.rapientrega.repositories.RepartidorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RepartidorService {

    private static final Logger log = LoggerFactory.getLogger(RepartidorService.class);

    private final RepartidorRepository repartidorRepository;
    private final PedidoRepository pedidoRepository;

    public RepartidorService(RepartidorRepository repartidorRepository, PedidoRepository pedidoRepository) {
        this.repartidorRepository = repartidorRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional(readOnly = true)
    public List<Repartidor> consultarDisponibles() {
        return repartidorRepository.findByDisponible(true);
    }

    @Transactional(readOnly = true)
    public List<Repartidor> consultarTodos() {
        return repartidorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Repartidor consultarPorId(Long id) {
        return repartidorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repartidor no encontrado con ID: " + id));
    }

    @Transactional
    public Repartidor actualizarUbicacion(Long id, Double latitud, Double longitud) {
        if (latitud == null || latitud < -90.0 || latitud > 90.0) {
            throw new BusinessRuleException("Latitud fuera de rango válido (-90.0 a 90.0)");
        }
        if (longitud == null || longitud < -180.0 || longitud > 180.0) {
            throw new BusinessRuleException("Longitud fuera de rango válido (-180.0 a 180.0)");
        }

        Repartidor repartidor = consultarPorId(id);
        repartidor.setLatitud(latitud);
        repartidor.setLongitud(longitud);
        return repartidorRepository.save(repartidor);
    }

    @Transactional
    public Repartidor actualizarDisponibilidad(Long id, Boolean disponible) {
        if (disponible == null) {
            throw new BusinessRuleException("El estado de disponibilidad no puede ser nulo");
        }

        Repartidor repartidor = consultarPorId(id);

        // Si se intenta marcar como DISPONIBLE, validar que no tenga pedidos activos asignados
        if (Boolean.TRUE.equals(disponible)) {
            boolean tienePedidosActivos = tieneAsignacionesActivas(id);
            if (tienePedidosActivos) {
                throw new BusinessRuleException("No se puede marcar como disponible al repartidor " + id +
                        " porque tiene entregas activas en curso");
            }
        }

        repartidor.setDisponible(disponible);
        return repartidorRepository.save(repartidor);
    }

    @Transactional(readOnly = true)
    public boolean tieneAsignacionesActivas(Long repartidorId) {
        List<Pedido> pedidosRepartidor = pedidoRepository.findByRepartidorId(repartidorId);
        for (Pedido p : pedidosRepartidor) {
            String estado = p.getEstado();
            if (PedidoService.ESTADO_ASIGNADO.equalsIgnoreCase(estado) ||
                PedidoService.ESTADO_EN_PREPARACION.equalsIgnoreCase(estado) ||
                PedidoService.ESTADO_EN_CAMINO.equalsIgnoreCase(estado)) {
                return true;
            }
        }
        return false;
    }
}
