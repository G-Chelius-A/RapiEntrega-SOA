package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Calificacion;
import com.api.rapientrega.services.CalificacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/calificaciones")
public class CalificacionController {

    private final CalificacionService calificacionService;

    public CalificacionController(CalificacionService calificacionService) {
        this.calificacionService = calificacionService;
    }

    @PostMapping("/pedidos/{pedidoId}")
    public ResponseEntity<Calificacion> calificarPedido(
            @PathVariable Long pedidoId,
            @RequestParam Integer calificacion,
            @RequestParam(required = false) String comentario) {
        Calificacion nuevaCalificacion = calificacionService.registrarCalificacion(pedidoId, calificacion, comentario);
        return ResponseEntity.status(HttpStatus.CREATED).body(nuevaCalificacion);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Calificacion> consultarPorId(@PathVariable Long id) {
        Calificacion calificacion = calificacionService.consultarPorId(id);
        return ResponseEntity.ok(calificacion);
    }

    @GetMapping("/pedidos/{pedidoId}")
    public ResponseEntity<List<Calificacion>> consultarPorPedido(@PathVariable Long pedidoId) {
        List<Calificacion> calificaciones = calificacionService.consultarPorPedidoId(pedidoId);
        return ResponseEntity.ok(calificaciones);
    }
}
