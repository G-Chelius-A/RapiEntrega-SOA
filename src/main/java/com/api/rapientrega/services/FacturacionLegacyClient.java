package com.api.rapientrega.services;

import java.math.BigDecimal;
import java.util.List;

public interface FacturacionLegacyClient {

    record FacturaDetalleItem(
            String descripcion,
            int cantidad,
            BigDecimal precioUnitario
    ) {}

    record SolicitudFacturacionRequest(
            Long pedidoId,
            String clienteNombre,
            String clienteEmail,
            String clienteDireccion,
            BigDecimal total,
            List<FacturaDetalleItem> items
    ) {}

    record SolicitudFacturacionResponse(
            boolean exitoso,
            String codigoFacturaLegacy,
            String mensajeError
    ) {}

    SolicitudFacturacionResponse emitirFactura(SolicitudFacturacionRequest request);

    SolicitudFacturacionResponse consultarFactura(String codigoFacturaLegacy);
}
