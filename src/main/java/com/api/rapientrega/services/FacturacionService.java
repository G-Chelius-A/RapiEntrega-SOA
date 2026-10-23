package com.api.rapientrega.services;

import com.api.rapientrega.models.Cliente;
import com.api.rapientrega.models.DetallePedido;
import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.repositories.DetallePedidoRepository;
import com.api.rapientrega.repositories.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FacturacionService {

    private static final Logger log = LoggerFactory.getLogger(FacturacionService.class);

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final FacturacionLegacyClient facturacionLegacyClient;

    public FacturacionService(PedidoRepository pedidoRepository,
                              DetallePedidoRepository detallePedidoRepository,
                              FacturacionLegacyClient facturacionLegacyClient) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.facturacionLegacyClient = facturacionLegacyClient;
    }

    /**
     * Solicita la emisión de factura ante el sistema heredado 2012.
     * Momento propuesto de invocación: una vez que el pago esté confirmado o el pedido sea aceptado/entregado.
     */
    @Transactional(readOnly = true)
    public FacturacionLegacyClient.SolicitudFacturacionResponse solicitarFacturacionPedido(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + pedidoId));

        List<DetallePedido> detalles = detallePedidoRepository.findByPedidoId(pedidoId);
        if (detalles.isEmpty()) {
            throw new BusinessRuleException("El pedido no cuenta con detalles para facturación");
        }

        Cliente cliente = pedido.getCliente();

        List<FacturacionLegacyClient.FacturaDetalleItem> items = new java.util.ArrayList<>();
        for (DetallePedido d : detalles) {
            items.add(new FacturacionLegacyClient.FacturaDetalleItem(
                    d.getProducto(),
                    d.getCantidad(),
                    d.getPrecioUnitario()
            ));
        }

        FacturacionLegacyClient.SolicitudFacturacionRequest request = new FacturacionLegacyClient.SolicitudFacturacionRequest(
                pedido.getId(),
                cliente.getNombre(),
                cliente.getEmail(),
                cliente.getDireccion(),
                pedido.getTotal(),
                items
        );

        try {
            FacturacionLegacyClient.SolicitudFacturacionResponse response = facturacionLegacyClient.emitirFactura(request);
            if (response.exitoso()) {
                log.info("Factura para pedido {} emitida exitosamente en sistema legacy: {}", pedidoId, response.codigoFacturaLegacy());
            } else {
                log.warn("Fallo al emitir factura para pedido {} en sistema legacy: {}", pedidoId, response.mensajeError());
            }
            return response;
        } catch (Exception ex) {
            log.error("Excepción al comunicarse con el sistema de facturación legacy: {}", ex.getMessage());
            return new FacturacionLegacyClient.SolicitudFacturacionResponse(false, null, "Error de comunicación con facturación: " + ex.getMessage());
        }
    }

    public FacturacionLegacyClient.SolicitudFacturacionResponse consultarFactura(String codigoFacturaLegacy) {
        return facturacionLegacyClient.consultarFactura(codigoFacturaLegacy);
    }
}
