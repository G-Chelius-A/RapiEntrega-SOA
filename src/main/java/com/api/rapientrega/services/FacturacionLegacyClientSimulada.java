package com.api.rapientrega.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FacturacionLegacyClientSimulada implements FacturacionLegacyClient {

    private static final Logger log = LoggerFactory.getLogger(FacturacionLegacyClientSimulada.class);

    @Override
    public SolicitudFacturacionResponse emitirFactura(SolicitudFacturacionRequest request) {
        log.info("Simulando emisión de factura en sistema heredado 2012 para pedido {}", request.pedidoId());
        if (request.total() == null || request.total().signum() <= 0) {
            return new SolicitudFacturacionResponse(false, null, "Importe total inválido para sistema heredado");
        }
        if (request.clienteEmail() != null && request.clienteEmail().contains("errorfacturacion")) {
            log.warn("Simulación: Error de comunicación con sistema heredado");
            return new SolicitudFacturacionResponse(false, null, "Sistema heredado no disponible (Error 500 Legacy Core)");
        }

        String folio = "FAC2012-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Factura emitida exitosamente en sistema heredado con folio: {}", folio);
        return new SolicitudFacturacionResponse(true, folio, null);
    }

    @Override
    public SolicitudFacturacionResponse consultarFactura(String codigoFacturaLegacy) {
        log.info("Simulando consulta de factura legacy: {}", codigoFacturaLegacy);
        if (codigoFacturaLegacy != null && !codigoFacturaLegacy.isBlank()) {
            return new SolicitudFacturacionResponse(true, codigoFacturaLegacy, "Factura válida en archivo central");
        }
        return new SolicitudFacturacionResponse(false, null, "Factura no encontrada en sistema heredado");
    }
}
