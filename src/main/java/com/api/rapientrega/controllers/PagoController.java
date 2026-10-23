package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Pago;
import com.api.rapientrega.services.PagoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pago> consultarPorId(@PathVariable("id") Long id) {
        Pago pago = pagoService.consultarPorId(id);
        return ResponseEntity.ok(pago);
    }

    @GetMapping
    public ResponseEntity<List<Pago>> listarPagos(
            @RequestParam(name = "pedidoId", required = false) Long pedidoId,
            @RequestParam(name = "estado", required = false) String estado) {

        if (pedidoId != null) {
            return ResponseEntity.ok(pagoService.consultarPorPedidoId(pedidoId));
        }
        if (estado != null && !estado.isBlank()) {
            return ResponseEntity.ok(pagoService.consultarPorEstado(estado));
        }
        return ResponseEntity.badRequest().build();
    }
}
