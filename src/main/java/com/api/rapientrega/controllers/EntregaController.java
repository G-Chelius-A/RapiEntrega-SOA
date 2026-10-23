package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.models.Repartidor;
import com.api.rapientrega.services.EntregaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/entregas")
public class EntregaController {

    private final EntregaService entregaService;

    public EntregaController(EntregaService entregaService) {
        this.entregaService = entregaService;
    }

    @PostMapping("/pedidos/{pedidoId}/asignar")
    public ResponseEntity<Pedido> asignarRepartidor(@PathVariable("pedidoId") Long pedidoId) {
        Pedido pedido = entregaService.asignarRepartidorMasCercano(pedidoId);
        return ResponseEntity.ok(pedido);
    }

    @PostMapping("/pedidos/{pedidoId}/iniciar")
    public ResponseEntity<Pedido> iniciarEntrega(@PathVariable("pedidoId") Long pedidoId) {
        Pedido pedido = entregaService.iniciarEntrega(pedidoId);
        return ResponseEntity.ok(pedido);
    }

    @PostMapping("/pedidos/{pedidoId}/completar")
    public ResponseEntity<Pedido> completarEntrega(@PathVariable("pedidoId") Long pedidoId) {
        Pedido pedido = entregaService.completarEntrega(pedidoId);
        return ResponseEntity.ok(pedido);
    }

    @GetMapping("/pedidos/{pedidoId}/seguimiento")
    public ResponseEntity<Repartidor> consultarSeguimiento(@PathVariable("pedidoId") Long pedidoId) {
        Repartidor repartidor = entregaService.consultarSeguimientoPedido(pedidoId);
        return ResponseEntity.ok(repartidor);
    }

    @PatchMapping("/repartidores/{repartidorId}/ubicacion")
    public ResponseEntity<Repartidor> actualizarUbicacionRepartidor(
            @PathVariable("repartidorId") Long repartidorId,
            @RequestParam(name = "latitud") Double latitud,
            @RequestParam(name = "longitud") Double longitud) {
        Repartidor repartidor = entregaService.actualizarUbicacionRepartidor(repartidorId, latitud, longitud);
        return ResponseEntity.ok(repartidor);
    }
}
