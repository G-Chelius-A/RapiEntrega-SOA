package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Reembolso;
import com.api.rapientrega.services.ReembolsoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reembolsos")
public class ReembolsoController {

    private final ReembolsoService reembolsoService;

    public ReembolsoController(ReembolsoService reembolsoService) {
        this.reembolsoService = reembolsoService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Reembolso> consultarPorId(@PathVariable("id") Long id) {
        Reembolso reembolso = reembolsoService.consultarPorId(id);
        return ResponseEntity.ok(reembolso);
    }

    @GetMapping
    public ResponseEntity<List<Reembolso>> listarReembolsos(
            @RequestParam(name = "pagoId", required = false) Long pagoId,
            @RequestParam(name = "estado", required = false) String estado) {

        if (pagoId != null) {
            return ResponseEntity.ok(reembolsoService.consultarPorPagoId(pagoId));
        }
        if (estado != null && !estado.isBlank()) {
            return ResponseEntity.ok(reembolsoService.consultarPorEstado(estado));
        }
        return ResponseEntity.badRequest().build();
    }
}
