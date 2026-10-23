package com.api.rapientrega.services;

import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.repositories.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PedidoSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PedidoSchedulerService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoService pedidoService;
    private final ReembolsoService reembolsoService;

    public PedidoSchedulerService(PedidoRepository pedidoRepository,
                                  PedidoService pedidoService,
                                  ReembolsoService reembolsoService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoService = pedidoService;
        this.reembolsoService = reembolsoService;
    }

    /**
     * Revisa cada 30 segundos pedidos pendientes de aceptación cuyo plazo de 5 minutos haya expirado.
     * La cancelación atómica con updateEstadoCondicional garantiza seguridad si el restaurante
     * intenta aceptar concurrentemente.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void verificarPedidosVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Pedido> pedidosVencidos = pedidoRepository.findVencidos(PedidoService.ESTADO_PENDIENTE_ACEPTACION, ahora);

        if (!pedidosVencidos.isEmpty()) {
            log.info("Scheduler detectó {} pedidos vencidos pendientes de aceptación", pedidosVencidos.size());
        }

        for (Pedido p : pedidosVencidos) {
            try {
                boolean cancelado = pedidoService.cancelarPorVencimiento(p.getId());
                if (cancelado) {
                    log.info("Scheduler canceló con éxito el pedido vencido ID: {}", p.getId());
                } else {
                    log.info("El pedido ID: {} ya fue aceptado o procesado previamente", p.getId());
                }
            } catch (Exception e) {
                log.error("Error al procesar cancelación por vencimiento para pedido ID {}: {}", p.getId(), e.getMessage());
            }
        }

        // Conciliar reembolsos pendientes de forma periódica
        try {
            reembolsoService.conciliarReembolsosPendientes();
        } catch (Exception e) {
            log.error("Error en conciliación periódica de reembolsos: {}", e.getMessage());
        }
    }
}
