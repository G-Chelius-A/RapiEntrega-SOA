package com.api.rapientrega.services;

import com.api.rapientrega.models.Pago;
import com.api.rapientrega.models.Reembolso;
import com.api.rapientrega.repositories.PagoRepository;
import com.api.rapientrega.repositories.ReembolsoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReembolsoService {

    private static final Logger log = LoggerFactory.getLogger(ReembolsoService.class);

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_COMPLETADO = "COMPLETADO";
    public static final String ESTADO_FALLIDO = "FALLIDO";

    private final ReembolsoRepository reembolsoRepository;
    private final PagoRepository pagoRepository;
    private final PasarelaPagoClient pasarelaPagoClient;

    public ReembolsoService(ReembolsoRepository reembolsoRepository,
                            PagoRepository pagoRepository,
                            PasarelaPagoClient pasarelaPagoClient) {
        this.reembolsoRepository = reembolsoRepository;
        this.pagoRepository = pagoRepository;
        this.pasarelaPagoClient = pasarelaPagoClient;
    }

    @Transactional
    public Reembolso solicitarReembolso(Long pagoId, String motivo) {
        Pago pago = pagoRepository.findById(pagoId)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado con ID: " + pagoId));

        if (!PagoService.ESTADO_COMPLETADO.equalsIgnoreCase(pago.getEstado())) {
            throw new BusinessRuleException("Solo se pueden reembolsar pagos completados. Estado actual: " + pago.getEstado());
        }

        List<Reembolso> reembolsosExistentes = reembolsoRepository.findByPagoId(pagoId);
        for (Reembolso r : reembolsosExistentes) {
            if (ESTADO_COMPLETADO.equalsIgnoreCase(r.getEstado())) {
                log.info("El pago {} ya cuenta con un reembolso completado (ID: {})", pagoId, r.getId());
                return r;
            }
            if (ESTADO_PENDIENTE.equalsIgnoreCase(r.getEstado())) {
                log.info("El pago {} ya tiene un reembolso pendiente de confirmación (ID: {})", pagoId, r.getId());
                return r;
            }
        }

        Reembolso nuevoReembolso = new Reembolso(null, pago, ESTADO_PENDIENTE);
        Reembolso reembolsoGuardado = reembolsoRepository.save(nuevoReembolso);

        // Procesar contra la pasarela
        try {
            PasarelaPagoClient.ReembolsoRequest request = new PasarelaPagoClient.ReembolsoRequest(
                    pago.getId(),
                    "TX-" + pago.getId(),
                    pago.getMonto(),
                    motivo
            );

            PasarelaPagoClient.ReembolsoResponse response = pasarelaPagoClient.procesarReembolso(request);

            if (response.exitoso()) {
                reembolsoGuardado.setEstado(ESTADO_COMPLETADO);
                log.info("Reembolso {} completado con éxito en pasarela: {}", reembolsoGuardado.getId(), response.transaccionReembolsoId());
            } else if (response.incierto()) {
                reembolsoGuardado.setEstado(ESTADO_PENDIENTE);
                log.warn("Reembolso {} en estado incierto por timeout/comunicación. Requiere conciliación posterior.", reembolsoGuardado.getId());
            } else {
                reembolsoGuardado.setEstado(ESTADO_FALLIDO);
                log.error("Reembolso {} rechazado por la pasarela: {}", reembolsoGuardado.getId(), response.mensaje());
            }
        } catch (Exception ex) {
            log.error("Error inesperado al contactar pasarela para reembolso {}: {}", reembolsoGuardado.getId(), ex.getMessage());
            reembolsoGuardado.setEstado(ESTADO_PENDIENTE);
        }

        return reembolsoRepository.save(reembolsoGuardado);
    }

    @Transactional(readOnly = true)
    public Reembolso consultarPorId(Long id) {
        return reembolsoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reembolso no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Reembolso> consultarPorPagoId(Long pagoId) {
        return reembolsoRepository.findByPagoId(pagoId);
    }

    @Transactional(readOnly = true)
    public List<Reembolso> consultarPorEstado(String estado) {
        return reembolsoRepository.findByEstado(estado);
    }

    @Transactional
    public void conciliarReembolsosPendientes() {
        List<Reembolso> pendientes = reembolsoRepository.findByEstado(ESTADO_PENDIENTE);
        for (Reembolso r : pendientes) {
            try {
                PasarelaPagoClient.ReembolsoRequest request = new PasarelaPagoClient.ReembolsoRequest(
                        r.getPago().getId(),
                        "TX-" + r.getPago().getId(),
                        r.getPago().getMonto(),
                        "CONCILIACION_PERIODICA"
                );
                PasarelaPagoClient.ReembolsoResponse response = pasarelaPagoClient.procesarReembolso(request);
                if (response.exitoso()) {
                    r.setEstado(ESTADO_COMPLETADO);
                    reembolsoRepository.save(r);
                    log.info("Reembolso {} conciliado exitosamente", r.getId());
                } else if (!response.incierto()) {
                    r.setEstado(ESTADO_FALLIDO);
                    reembolsoRepository.save(r);
                    log.warn("Reembolso {} marcado como fallido durante conciliación", r.getId());
                }
            } catch (Exception e) {
                log.error("Error al conciliar reembolso {}: {}", r.getId(), e.getMessage());
            }
        }
    }
}
