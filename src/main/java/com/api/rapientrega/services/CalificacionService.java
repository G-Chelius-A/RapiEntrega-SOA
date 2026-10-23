package com.api.rapientrega.services;

import com.api.rapientrega.models.Calificacion;
import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.repositories.CalificacionRepository;
import com.api.rapientrega.repositories.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;
    private final PedidoRepository pedidoRepository;

    public CalificacionService(CalificacionRepository calificacionRepository,
                               PedidoRepository pedidoRepository) {
        this.calificacionRepository = calificacionRepository;
        this.pedidoRepository = pedidoRepository;
    }

    @Transactional
    public Calificacion registrarCalificacion(Long pedidoId, Integer puntaje, String comentario) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!PedidoService.ESTADO_ENTREGADO.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("Solo se pueden calificar pedidos que hayan sido entregados. Estado actual: " + pedido.getEstado());
        }

        if (puntaje == null || puntaje < 1 || puntaje > 5) {
            throw new BusinessRuleException("La calificación debe estar entre 1 y 5");
        }

        if (comentario != null && comentario.length() > 255) {
            throw new BusinessRuleException("El comentario no puede exceder 255 caracteres");
        }

        List<Calificacion> calificacionesPrevias = calificacionRepository.findByPedidoId(pedidoId);
        if (!calificacionesPrevias.isEmpty()) {
            throw new BusinessRuleException("El pedido " + pedidoId + " ya cuenta con una calificación registrada");
        }

        Calificacion nuevaCalificacion = new Calificacion(null, pedido, puntaje, comentario);
        return calificacionRepository.save(nuevaCalificacion);
    }

    @Transactional(readOnly = true)
    public List<Calificacion> consultarPorPedidoId(Long pedidoId) {
        if (!pedidoRepository.existsById(pedidoId)) {
            throw new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId);
        }
        return calificacionRepository.findByPedidoId(pedidoId);
    }

    @Transactional(readOnly = true)
    public Calificacion consultarPorId(Long id) {
        return calificacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Calificación no encontrada con ID: " + id));
    }
}
