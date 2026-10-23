package com.api.rapientrega.repositories;

import com.api.rapientrega.models.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {
    List<Pedido> findByClienteId(Long clienteId);
    List<Pedido> findByRestauranteId(Long restauranteId);
    List<Pedido> findByRepartidorId(Long repartidorId);
    List<Pedido> findByEstado(String estado);

    @Query("SELECT p FROM Pedido p WHERE p.estado = :estado AND p.fechaLimiteAceptacion IS NOT NULL AND p.fechaLimiteAceptacion <= :ahora")
    List<Pedido> findVencidos(@Param("estado") String estado, @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("UPDATE Pedido p SET p.estado = :nuevoEstado WHERE p.id = :id AND p.estado = :estadoEsperado")
    int updateEstadoCondicional(@Param("id") Long id, @Param("estadoEsperado") String estadoEsperado, @Param("nuevoEstado") String nuevoEstado);

    @Modifying
    @Query("UPDATE Pedido p SET p.estado = :nuevoEstado, p.repartidor = :repartidor WHERE p.id = :id AND p.estado = :estadoEsperado")
    int asignarRepartidorCondicional(@Param("id") Long id, @Param("estadoEsperado") String estadoEsperado, @Param("nuevoEstado") String nuevoEstado, @Param("repartidor") com.api.rapientrega.models.Repartidor repartidor);
}
