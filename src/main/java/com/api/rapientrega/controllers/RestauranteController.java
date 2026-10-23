package com.api.rapientrega.controllers;

import com.api.rapientrega.models.Restaurante;
import com.api.rapientrega.services.RestauranteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurantes")
public class RestauranteController {

    private final RestauranteService restauranteService;

    public RestauranteController(RestauranteService restauranteService) {
        this.restauranteService = restauranteService;
    }

    @GetMapping
    public ResponseEntity<List<Restaurante>> listarRestaurantes(
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "nombre", required = false) String nombre) {

        if (nombre != null && !nombre.isBlank()) {
            return ResponseEntity.ok(restauranteService.buscarPorNombre(nombre));
        }
        if (categoria != null && !categoria.isBlank()) {
            return ResponseEntity.ok(restauranteService.buscarPorCategoria(categoria));
        }
        return ResponseEntity.ok(restauranteService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Restaurante> obtenerPorId(@PathVariable("id") Long id) {
        Restaurante restaurante = restauranteService.obtenerPorId(id);
        return ResponseEntity.ok(restaurante);
    }
}
