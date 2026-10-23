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

        // [PATRÓN ANTI-CORRUPCIÓN / ADAPTADOR]:
        // Los sistemas heredados (Legacy de 2012 basados en SOAP/XML o Mainframes)
        // suelen tener validaciones estrictas y formatos inflexibles.
        // Aquí validamos antes de invocar la interfaz heredada para evitar fallos catastróficos.
        if (request.total() == null || request.total().signum() <= 0) {
            return new SolicitudFacturacionResponse(false, null, "Importe total inválido para sistema heredado");
        }

        // [INYECCIÓN DE FALLAS CONTROLADA]:
        // En arquitecturas SOA es vital probar cómo reacciona el sistema ante caídas de servicios externos.
        // Usamos palabras clave en el email (como 'errorfacturacion') para forzar la respuesta 500 del Legacy
        // y comprobar que el pedido no se rompa, sino que maneje la excepción de forma resiliente.
        if (request.clienteEmail() != null && request.clienteEmail().contains("errorfacturacion")) {
            log.warn("Simulación: Error de comunicación con sistema heredado");
            return new SolicitudFacturacionResponse(false, null, "Sistema heredado no disponible (Error 500 Legacy Core)");
        }

        // Simulación del Folio Fiscal Digital o UUID emitido por el webservice SOAP heredado
        String folio = "FAC2012-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Factura emitida exitosamente en sistema heredado con folio: {}", folio);
        return new SolicitudFacturacionResponse(true, folio, null);
    }

    @Override
    public SolicitudFacturacionResponse consultarFactura(String codigoFacturaLegacy) {
        // [CONSULTA IDEMPOTENTE A SISTEMA EXTERNO]:
        // Permite verificar en el archivo central o bitácora fiscal del legacy
        // si la transacción fue completada, útil para reconciliación batch nocturna.
        log.info("Simulando consulta de factura legacy: {}", codigoFacturaLegacy);
        if (codigoFacturaLegacy != null && !codigoFacturaLegacy.isBlank()) {
            return new SolicitudFacturacionResponse(true, codigoFacturaLegacy, "Factura válida en archivo central");
        }
        return new SolicitudFacturacionResponse(false, null, "Factura no encontrada en sistema heredado");
    }
}
