package com.api.rapientrega.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PasarelaPagoClientSimulada implements PasarelaPagoClient {

    private static final Logger log = LoggerFactory.getLogger(PasarelaPagoClientSimulada.class);

    @Override
    public CobroResponse procesarCobro(CobroRequest request) {
        log.info("Simulando cobro en pasarela para pedido {}: monto={}", request.pedidoId(), request.monto());
        if (request.monto() == null || request.monto().signum() <= 0) {
            return new CobroResponse(false, false, null, "Monto inválido para el cobro");
        }
        if ("TARJETA_RECHAZADA".equalsIgnoreCase(request.referenciaCliente())) {
            log.warn("Simulación: Tarjeta rechazada por fondos insuficientes");
            return new CobroResponse(false, false, null, "Fondos insuficientes o tarjeta rechazada");
        }
        if ("TIMEOUT".equalsIgnoreCase(request.referenciaCliente())) {
            log.warn("Simulación: Timeout en comunicación con la pasarela");
            return new CobroResponse(false, true, null, "Timeout en la pasarela externa");
        }

        String transaccionId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new CobroResponse(true, false, transaccionId, "Cobro aprobado exitosamente");
    }

    @Override
    public ReembolsoResponse procesarReembolso(ReembolsoRequest request) {
        log.info("Simulando reembolso en pasarela para pago {}: monto={}", request.pagoId(), request.monto());
        if ("FALLO_REEMBOLSO".equalsIgnoreCase(request.motivo())) {
            log.warn("Simulación: Rechazo de reembolso por la pasarela");
            return new ReembolsoResponse(false, false, null, "Reembolso no autorizado por el emisor bancario");
        }
        if ("TIMEOUT_REEMBOLSO".equalsIgnoreCase(request.motivo())) {
            log.warn("Simulación: Timeout en reembolso");
            return new ReembolsoResponse(false, true, null, "Timeout en pasarela durante el reembolso");
        }

        String transaccionReembolsoId = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new ReembolsoResponse(true, false, transaccionReembolsoId, "Reembolso procesado exitosamente");
    }
}
