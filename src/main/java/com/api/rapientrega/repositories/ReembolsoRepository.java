package com.api.rapientrega.repositories;

import com.api.rapientrega.models.Reembolso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReembolsoRepository extends JpaRepository<Reembolso, Long> {
    List<Reembolso> findByPagoId(Long pagoId);
    List<Reembolso> findByEstado(String estado);
}
