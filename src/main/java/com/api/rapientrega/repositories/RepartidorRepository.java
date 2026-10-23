package com.api.rapientrega.repositories;

import com.api.rapientrega.models.Repartidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface RepartidorRepository extends JpaRepository<Repartidor, Long> {
    List<Repartidor> findByDisponible(Boolean disponible);

    @Modifying
    @Query("UPDATE Repartidor r SET r.disponible = :nuevoDisponible WHERE r.id = :id AND r.disponible = :esperadoDisponible")
    int updateDisponibilidadCondicional(@Param("id") Long id, @Param("esperadoDisponible") Boolean esperadoDisponible, @Param("nuevoDisponible") Boolean nuevoDisponible);
}
