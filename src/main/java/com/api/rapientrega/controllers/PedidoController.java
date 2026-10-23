package com.api.rapientrega.controllers;

import com.api.rapientrega.models.DetallePedido;
import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.services.PedidoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<Pedido> crearPedido(@RequestBody PedidoService.CrearPedidoRequest request) {
        Pedido pedidoCreado = pedidoService.registrarPedido(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoCreado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> consultarPorId(@PathVariable Long id) {
        Pedido pedido = pedidoService.consultarPorId(id);
        return ResponseEntity.ok(pedido);
    }

    @GetMapping("/{id}/detalles")
    public ResponseEntity<List<DetallePedido>> consultarDetalles(@PathVariable Long id) {
        List<DetallePedido> detalles = pedidoService.consultarDetalles(id);
        return ResponseEntity.ok(detalles);
    }

    @GetMapping
    public ResponseEntity<List<Pedido>> listarPedidos(
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long restauranteId,
            @RequestParam(required = false) String estado) {

        if (clienteId != null) {
            return ResponseEntity.ok(pedidoService.consultarPorClienteId(clienteId));
        }
        if (restauranteId != null) {
            return ResponseEntity.ok(pedidoService.consultarPorRestauranteId(restauranteId));
        }
        if (estado != null && !estado.isBlank()) {
            return ResponseEntity.ok(pedidoService.consultarPorEstado(estado));
        }
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/{id}/pagar")
    public ResponseEntity<Pedido> pagarPedido(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "TARJETA") String metodo,
            @RequestParam(required = false) String referenciaIdempotencia) {
        Pedido pedidoActualizado = pedidoService.pagarPedido(id, metodo, referenciaIdempotencia);
        return ResponseEntity.ok(pedidoActualizado);
    }

    @PostMapping("/{id}/aceptar")
    public ResponseEntity<Pedido> aceptarPedido(@PathVariable Long id) {
        Pedido pedidoAceptado = pedidoService.aceptarPedidoPorRestaurante(id);
        return ResponseEntity.ok(pedidoAceptado);
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Pedido> rechazarPedido(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Rechazado por el restaurante") String motivo) {
        Pedido pedidoRechazado = pedidoService.rechazarPedidoPorRestaurante(id, motivo);
        return ResponseEntity.ok(pedidoRechazado);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarPedidoPorVencimiento(@PathVariable Long id) {
        boolean cancelado = pedidoService.cancelarPorVencimiento(id);
        if (cancelado) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.badRequest().build();
    }
}
