package com.api.rapientrega.services;

import com.api.rapientrega.models.Restaurante;
import com.api.rapientrega.repositories.RestauranteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RestauranteService {

    private final RestauranteRepository restauranteRepository;

    public RestauranteService(RestauranteRepository restauranteRepository) {
        this.restauranteRepository = restauranteRepository;
    }

    @Transactional(readOnly = true)
    public List<Restaurante> obtenerTodos() {
        return restauranteRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Restaurante obtenerPorId(Long id) {
        return restauranteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Restaurante> buscarPorCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return restauranteRepository.findAll();
        }
        return restauranteRepository.findByCategoria(categoria.trim());
    }

    @Transactional(readOnly = true)
    public List<Restaurante> buscarPorNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return restauranteRepository.findAll();
        }
        return restauranteRepository.findByNombreContainingIgnoreCase(nombre.trim());
    }

    @Transactional(readOnly = true)
    public boolean existeRestaurante(Long id) {
        return restauranteRepository.existsById(id);
    }
}
