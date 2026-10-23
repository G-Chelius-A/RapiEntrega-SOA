package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Repartidor;
import com.api.rapientrega.services.RepartidorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/repartidores")
public class RepartidorController {

    private final RepartidorService repartidorService;

    public RepartidorController(RepartidorService repartidorService) {
        this.repartidorService = repartidorService;
    }

    @GetMapping
    public ResponseEntity<List<Repartidor>> listarRepartidores(@RequestParam(required = false) Boolean disponible) {
        if (Boolean.TRUE.equals(disponible)) {
            return ResponseEntity.ok(repartidorService.consultarDisponibles());
        }
        return ResponseEntity.ok(repartidorService.consultarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Repartidor> consultarPorId(@PathVariable Long id) {
        Repartidor repartidor = repartidorService.consultarPorId(id);
        return ResponseEntity.ok(repartidor);
    }

    @PatchMapping("/{id}/ubicacion")
    public ResponseEntity<Repartidor> actualizarUbicacion(
            @PathVariable Long id,
            @RequestParam Double latitud,
            @RequestParam Double longitud) {
        Repartidor repartidorActualizado = repartidorService.actualizarUbicacion(id, latitud, longitud);
        return ResponseEntity.ok(repartidorActualizado);
    }

    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<Repartidor> actualizarDisponibilidad(
            @PathVariable Long id,
            @RequestParam Boolean disponible) {
        Repartidor repartidorActualizado = repartidorService.actualizarDisponibilidad(id, disponible);
        return ResponseEntity.ok(repartidorActualizado);
    }
}
