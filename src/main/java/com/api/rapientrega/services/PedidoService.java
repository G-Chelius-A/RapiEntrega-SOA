package com.api.rapientrega.services;

import com.api.rapientrega.models.Cliente;
import com.api.rapientrega.models.DetallePedido;
import com.api.rapientrega.models.Pago;
import com.api.rapientrega.models.Pedido;
import com.api.rapientrega.models.Restaurante;
import com.api.rapientrega.repositories.ClienteRepository;
import com.api.rapientrega.repositories.DetallePedidoRepository;
import com.api.rapientrega.repositories.PedidoRepository;
import com.api.rapientrega.repositories.RestauranteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    // Estados oficiales del ciclo de vida del Pedido
    public static final String ESTADO_CREADO = "CREADO";
    public static final String ESTADO_PENDIENTE_PAGO = "PENDIENTE_PAGO";
    public static final String ESTADO_PENDIENTE_ACEPTACION = "PENDIENTE_ACEPTACION";
    public static final String ESTADO_ACEPTADO = "ACEPTADO";
    public static final String ESTADO_EN_PREPARACION = "EN_PREPARACION";
    public static final String ESTADO_ASIGNADO = "ASIGNADO";
    public static final String ESTADO_EN_CAMINO = "EN_CAMINO";
    public static final String ESTADO_ENTREGADO = "ENTREGADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";

    // Plazo máximo para que el restaurante acepte (5 minutos)
    public static final int PLAZO_ACEPTACION_MINUTOS = 5;

    public record ItemPedidoDto(
            String producto,
            Integer cantidad,
            BigDecimal precioUnitario
    ) {}

    public record CrearPedidoRequest(
            Long clienteId,
            Long restauranteId,
            List<ItemPedidoDto> items
    ) {}

    private final PedidoRepository pedidoRepository;
    private final DetallePedidoRepository detallePedidoRepository;
    private final ClienteRepository clienteRepository;
    private final RestauranteRepository restauranteRepository;
    private final PagoService pagoService;
    private final EntregaService entregaService;

    public PedidoService(PedidoRepository pedidoRepository,
                         DetallePedidoRepository detallePedidoRepository,
                         ClienteRepository clienteRepository,
                         RestauranteRepository restauranteRepository,
                         PagoService pagoService,
                         EntregaService entregaService) {
        this.pedidoRepository = pedidoRepository;
        this.detallePedidoRepository = detallePedidoRepository;
        this.clienteRepository = clienteRepository;
        this.restauranteRepository = restauranteRepository;
        this.pagoService = pagoService;
        this.entregaService = entregaService;
    }

    /**
     * 1. Registrar un pedido:
     * - Valida existencia de cliente y restaurante.
     * - Valida detalles (mínimo 1 item, cantidad >= 1, precio >= 0).
     * - Calcula el total en el servidor.
     * - El estado inicial es CREADO. No se inicia el cronómetro de 5 minutos aún.
     */
    @Transactional
    public Pedido registrarPedido(CrearPedidoRequest request) {
        if (request == null) {
            throw new BusinessRuleException("La solicitud de pedido no puede ser nula");
        }

        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado con ID: " + request.clienteId()));

        Restaurante restaurante = restauranteRepository.findById(request.restauranteId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurante no encontrado con ID: " + request.restauranteId()));

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessRuleException("El pedido debe contener al menos un producto");
        }

        BigDecimal totalCalculado = BigDecimal.ZERO;
        List<DetallePedido> detallesAInsertar = new ArrayList<>();

        for (ItemPedidoDto item : request.items()) {
            if (item.producto() == null || item.producto().isBlank()) {
                throw new BusinessRuleException("El nombre del producto no puede estar vacío");
            }
            if (item.cantidad() == null || item.cantidad() <= 0) {
                throw new BusinessRuleException("La cantidad de cada producto debe ser mayor a cero");
            }
            if (item.precioUnitario() == null || item.precioUnitario().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessRuleException("El precio unitario no puede ser negativo");
            }

            BigDecimal subtotal = item.precioUnitario().multiply(BigDecimal.valueOf(item.cantidad()));
            totalCalculado = totalCalculado.add(subtotal);
        }

        Pedido pedido = new Pedido(
                null,
                cliente,
                restaurante,
                null, // Repartidor no asignado inicialmente
                ESTADO_CREADO,
                LocalDateTime.now(),
                totalCalculado,
                null, // fechaEntrega nula
                null  // fechaLimiteAceptacion se define tras confirmación de pago
        );

        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        for (ItemPedidoDto item : request.items()) {
            DetallePedido detalle = new DetallePedido(
                    null,
                    pedidoGuardado,
                    item.producto().trim(),
                    item.cantidad(),
                    item.precioUnitario()
            );
            detallePedidoRepository.save(detalle);
        }

        log.info("Pedido {} registrado con total {}", pedidoGuardado.getId(), totalCalculado);
        return pedidoGuardado;
    }

    /**
     * 9 y 10. Coordinar procesamiento del pago:
     * Si el pago es exitoso, formaliza el inicio del plazo de 5 minutos para el restaurante
     * pasando el estado a PENDIENTE_ACEPTACION.
     */
    @Transactional
    public Pedido pagarPedido(Long pedidoId, String metodo, String referenciaIdempotencia) {
        Pedido pedido = consultarPorId(pedidoId);

        if (!ESTADO_CREADO.equalsIgnoreCase(pedido.getEstado()) &&
            !ESTADO_PENDIENTE_PAGO.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("El pedido no se encuentra en estado para procesar pago. Estado actual: " + pedido.getEstado());
        }

        Pago pago = pagoService.procesarPago(pedidoId, metodo, referenciaIdempotencia);

        if (PagoService.ESTADO_COMPLETADO.equalsIgnoreCase(pago.getEstado())) {
            LocalDateTime ahora = LocalDateTime.now();
            pedido.setEstado(ESTADO_PENDIENTE_ACEPTACION);
            pedido.setFechaLimiteAceptacion(ahora.plusMinutes(PLAZO_ACEPTACION_MINUTOS));
            log.info("Pago confirmado para pedido {}. Plazo de 5 minutos iniciado. Límite: {}", pedidoId, pedido.getFechaLimiteAceptacion());
        } else if (PagoService.ESTADO_RECHAZADO.equalsIgnoreCase(pago.getEstado())) {
            pedido.setEstado(ESTADO_PENDIENTE_PAGO);
            log.warn("Pago rechazado para pedido {}. Permanece en PENDIENTE_PAGO.", pedidoId);
        } else {
            // INCIERTO o PENDIENTE
            pedido.setEstado(ESTADO_PENDIENTE_PAGO);
            log.warn("Pago en estado incierto para pedido {}. Se mantiene sin presentar al restaurante hasta confirmación.", pedidoId);
        }

        return pedidoRepository.save(pedido);
    }

    /**
     * 11. Restaurante acepta el pedido:
     * - Verifica que no haya vencido el plazo de 5 minutos.
     * - Utiliza actualización condicional para evitar conflicto con el scheduler de cancelación.
     * - Si se acepta a tiempo, coordina la asignación del repartidor.
     */
    @Transactional
    public Pedido aceptarPedidoPorRestaurante(Long pedidoId) {
        Pedido pedido = consultarPorId(pedidoId);

        if (!ESTADO_PENDIENTE_ACEPTACION.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("El pedido no está pendiente de aceptación. Estado actual: " + pedido.getEstado());
        }

        LocalDateTime ahora = LocalDateTime.now();
        if (pedido.getFechaLimiteAceptacion() != null && ahora.isAfter(pedido.getFechaLimiteAceptacion())) {
            // Venció antes de la llamada: cancelar y reembolsar de forma segura
            cancelarPorVencimiento(pedidoId);
            throw new BusinessRuleException("El plazo de 5 minutos para aceptar el pedido ha vencido. El pedido ha sido cancelado.");
        }

        // Transición atómica condicional
        int actualizados = pedidoRepository.updateEstadoCondicional(pedidoId, ESTADO_PENDIENTE_ACEPTACION, ESTADO_ACEPTADO);
        if (actualizados == 0) {
            throw new BusinessRuleException("No se pudo aceptar el pedido; pudo haber sido cancelado concurrentemente por vencimiento");
        }

        pedido = consultarPorId(pedidoId);
        pedido.setEstado(ESTADO_ACEPTADO);

        // Coordinar asignación de repartidor
        try {
            pedido = entregaService.asignarRepartidorMasCercano(pedidoId);
        } catch (Exception ex) {
            log.warn("No se pudo completar asignación inmediata de repartidor para pedido {}: {}", pedidoId, ex.getMessage());
        }

        return pedido;
    }

    /**
     * 11. Restaurante rechaza el pedido:
     * - Pasa a RECHAZADO.
     * - Solicita el reembolso correspondiente inmediatamente.
     */
    @Transactional
    public Pedido rechazarPedidoPorRestaurante(Long pedidoId, String motivoRechazo) {
        Pedido pedido = consultarPorId(pedidoId);

        if (!ESTADO_PENDIENTE_ACEPTACION.equalsIgnoreCase(pedido.getEstado())) {
            throw new BusinessRuleException("El pedido no está pendiente de aceptación. Estado actual: " + pedido.getEstado());
        }

        int actualizados = pedidoRepository.updateEstadoCondicional(pedidoId, ESTADO_PENDIENTE_ACEPTACION, ESTADO_RECHAZADO);
        if (actualizados == 0) {
            throw new BusinessRuleException("No se pudo rechazar el pedido debido a un cambio concurrente de estado");
        }

        log.info("Pedido {} rechazado por el restaurante. Procediendo a solicitar reembolso...", pedidoId);
        pagoService.solicitarReembolsoDePedido(pedidoId, "Rechazado por restaurante: " + motivoRechazo);

        return consultarPorId(pedidoId);
    }

    /**
     * 12. Vencimiento del plazo de 5 minutos:
     * - Cambia condicionalmente de PENDIENTE_ACEPTACION a CANCELADO.
     * - Solicita reembolso.
     */
    @Transactional
    public boolean cancelarPorVencimiento(Long pedidoId) {
        int actualizados = pedidoRepository.updateEstadoCondicional(pedidoId, ESTADO_PENDIENTE_ACEPTACION, ESTADO_CANCELADO);
        if (actualizados > 0) {
            log.info("Pedido {} cancelado exitosamente por vencimiento del plazo de 5 minutos", pedidoId);
            pagoService.solicitarReembolsoDePedido(pedidoId, "Cancelación por vencimiento de 5 minutos");
            return true;
        }
        return false;
    }

    /**
     * 15. Registrar la entrega final
     */
    @Transactional
    public Pedido registrarEntregaFinal(Long pedidoId) {
        return entregaService.completarEntrega(pedidoId);
    }

    @Transactional(readOnly = true)
    public Pedido consultarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Pedido> consultarPorClienteId(Long clienteId) {
        if (!clienteRepository.existsById(clienteId)) {
            throw new ResourceNotFoundException("Cliente no encontrado con ID: " + clienteId);
        }
        return pedidoRepository.findByClienteId(clienteId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> consultarPorRestauranteId(Long restauranteId) {
        return pedidoRepository.findByRestauranteId(restauranteId);
    }

    @Transactional(readOnly = true)
    public List<Pedido> consultarPorEstado(String estado) {
        return pedidoRepository.findByEstado(estado);
    }

    @Transactional(readOnly = true)
    public List<DetallePedido> consultarDetalles(Long pedidoId) {
        return detallePedidoRepository.findByPedidoId(pedidoId);
    }
}
