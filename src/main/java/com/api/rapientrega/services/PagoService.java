package com.api.rapientrega.services;

import com.api.rapientrega.models.Pago;
import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.models.Reembolso;
import com.api.rapientrega.repositories.PagoRepository;
import com.api.rapientrega.repositories.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    public static final String METODO_TARJETA = "TARJETA";
    public static final String METODO_EFECTIVO = "EFECTIVO";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String ESTADO_INCIERTO = "INCIERTO";

    private final PagoRepository pagoRepository;
    private final PedidoRepository pedidoRepository;
    private final PasarelaPagoClient pasarelaPagoClient;
    private final ReembolsoService reembolsoService;

    public PagoService(PagoRepository pagoRepository,
                       PedidoRepository pedidoRepository,
                       PasarelaPagoClient pasarelaPagoClient,
                       ReembolsoService reembolsoService) {
        this.pagoRepository = pagoRepository;
        this.pedidoRepository = pedidoRepository;
        this.pasarelaPagoClient = pasarelaPagoClient;
        this.reembolsoService = reembolsoService;
    }

    @Transactional
    public Pago procesarPago(Long pedidoId, String metodo, String referenciaIdempotencia) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        if (!PedidoService.ESTADO_CREADO.equalsIgnoreCase(pedido.getEstado()) &&
            !PedidoService.ESTADO_PENDIENTE_PAGO.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("El pedido no está en un estado apto para pago. Estado actual: " + pedido.getEstado());
        }

        // Idempotencia: Verificar pagos previos
        List<Pago> pagosPrevios = pagoRepository.findByPedidoId(pedidoId);
        for (Pago p : pagosPrevios) {
            if (ESTADO_COMPLETADO.equalsIgnoreCase(p.getEstado())) {
                log.info("El pedido {} ya tiene un pago completado (ID: {})", pedidoId, p.getId());
                return p;
            }
            if (ESTADO_PENDIENTE.equalsIgnoreCase(p.getEstado()) || ESTADO_INCIERTO.equalsIgnoreCase(p.getEstado())) {
                log.warn("El pedido {} ya tiene un pago en curso o incierto (ID: {}). No se duplica el cobro.", pedidoId, p.getId());
                return p;
            }
        }

        BigDecimal montoCobro = pedido.getTotal();
        if (montoCobro == null || montoCobro.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("El monto a cobrar debe ser mayor a cero. Total actual: " + montoCobro);
        }

        String metodoNormalizado = (metodo != null && !metodo.isBlank()) ? metodo.trim().toUpperCase() : METODO_TARJETA;

        // Registrar pago inicialmente como PENDIENTE
        Pago nuevoPago = new Pago(
                null,
                montoCobro,
                LocalDateTime.now(),
                metodoNormalizado,
                pedido,
                ESTADO_PENDIENTE
        );
        Pago pagoGuardado = pagoRepository.save(nuevoPago);

        // Llamar a la pasarela externa
        try {
            PasarelaPagoClient.CobroRequest request = new PasarelaPagoClient.CobroRequest(
                    pedido.getId(),
                    montoCobro,
                    metodoNormalizado,
                    referenciaIdempotencia
            );

            PasarelaPagoClient.CobroResponse response = pasarelaPagoClient.procesarCobro(request);

            if (response.exitoso()) {
                pagoGuardado.setEstado(ESTADO_COMPLETADO);
                log.info("Cobro confirmado para pedido {} con tx {}", pedidoId, response.transaccionId());
            } else if (response.incierto()) {
                pagoGuardado.setEstado(ESTADO_INCIERTO);
                log.warn("Resultado de pago incierto para pedido {}: {}", pedidoId, response.mensaje());
            } else {
                pagoGuardado.setEstado(ESTADO_RECHAZADO);
                log.warn("Cobro rechazado para pedido {}: {}", pedidoId, response.mensaje());
            }
        } catch (Exception ex) {
            log.error("Excepción al comunicarse con la pasarela para pedido {}: {}", pedidoId, ex.getMessage());
            pagoGuardado.setEstado(ESTADO_INCIERTO);
        }

        return pagoRepository.save(pagoGuardado);
    }

    @Transactional
    public Reembolso solicitarReembolsoDePedido(Long pedidoId, String motivo) {
        List<Pago> pagos = pagoRepository.findByPedidoId(pedidoId);
        Pago pagoCompletado = null;
        for (Pago p : pagos) {
            if (ESTADO_COMPLETADO.equalsIgnoreCase(p.getEstado())) {
                pagoCompletado = p;
                break;
            }
        }

        if (pagoCompletado == null) {
            log.warn("No existe un pago completado para reembolsar en el pedido {}", pedidoId);
            return null;
        }

        return reembolsoService.solicitarReembolso(pagoCompletado.getId(), motivo);
    }

    @Transactional(readOnly = true)
    public Pago consultarPorId(Long id) {
        return pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Pago> consultarPorPedidoId(Long pedidoId) {
        return pagoRepository.findByPedidoId(pedidoId);
    }

    @Transactional(readOnly = true)
    public List<Pago> consultarPorEstado(String estado) {
        return pagoRepository.findByEstado(estado);
    }
}
