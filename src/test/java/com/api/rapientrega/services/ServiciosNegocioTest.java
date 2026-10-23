package com.api.rapientrega.services;

import com.api.rapientrega.models.*;
import com.api.rapientrega.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiciosNegocioTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private DetallePedidoRepository detallePedidoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private RestauranteRepository restauranteRepository;
    @Mock
    private RepartidorRepository repartidorRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ReembolsoRepository reembolsoRepository;
    @Mock
    private CalificacionRepository calificacionRepository;
    @Mock
    private PasarelaPagoClient pasarelaPagoClient;
    @Mock
    private FacturacionLegacyClient facturacionLegacyClient;

    private PagoService pagoService;
    private ReembolsoService reembolsoService;
    private RepartidorService repartidorService;
    private EntregaService entregaService;
    private PedidoService pedidoService;
    private CalificacionService calificacionService;
    private FacturacionService facturacionService;

    private Cliente clienteEjemplo;
    private Restaurante restauranteEjemplo;
    private Repartidor repartidorEjemplo;

    @BeforeEach
    void setUp() {
        reembolsoService = new ReembolsoService(reembolsoRepository, pagoRepository, pasarelaPagoClient);
        pagoService = new PagoService(pagoRepository, pedidoRepository, pasarelaPagoClient, reembolsoService);
        repartidorService = new RepartidorService(repartidorRepository, pedidoRepository);
        entregaService = new EntregaService(repartidorRepository, pedidoRepository, repartidorService);
        pedidoService = new PedidoService(pedidoRepository, detallePedidoRepository, clienteRepository, restauranteRepository, pagoService, entregaService);
        calificacionService = new CalificacionService(calificacionRepository, pedidoRepository);
        facturacionService = new FacturacionService(pedidoRepository, detallePedidoRepository, facturacionLegacyClient);

        clienteEjemplo = new Cliente(1L, "Juan Perez", "Calle 123", "juan@test.com");
        restauranteEjemplo = new Restaurante(1L, "Pizzeria Central", "Av 1", "Italiana", 19.4326, -99.1332);
        repartidorEjemplo = new Repartidor(1L, "Carlos Moto", 19.4350, -99.1350, true);
    }

    // 1. Creación válida de un pedido y cálculo del total en servidor
    @Test
    @DisplayName("Escenarios 1 y 4: Creación válida de pedido y cálculo correcto del total en servidor")
    void testCrearPedidoValido() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteEjemplo));
        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restauranteEjemplo));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> {
            Pedido p = i.getArgument(0);
            p.setId(10L);
            return p;
        });

        List<PedidoService.ItemPedidoDto> items = List.of(
                new PedidoService.ItemPedidoDto("Pizza Familiar", 2, new BigDecimal("150.00")),
                new PedidoService.ItemPedidoDto("Refresco", 3, new BigDecimal("25.00"))
        );
        PedidoService.CrearPedidoRequest req = new PedidoService.CrearPedidoRequest(1L, 1L, items);

        Pedido resultado = pedidoService.registrarPedido(req);

        assertNotNull(resultado);
        assertEquals(PedidoService.ESTADO_CREADO, resultado.getEstado());
        assertEquals(new BigDecimal("375.00"), resultado.getTotal()); // 2*150 + 3*25 = 375
        verify(detallePedidoRepository, times(2)).save(any(DetallePedido.class));
    }

    // 2. Pedido sin detalles
    @Test
    @DisplayName("Escenario 2: Pedido sin detalles debe lanzar excepción de negocio")
    void testPedidoSinDetalles() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteEjemplo));
        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restauranteEjemplo));

        PedidoService.CrearPedidoRequest req = new PedidoService.CrearPedidoRequest(1L, 1L, List.of());
        assertThrows(BusinessRuleException.class, () -> pedidoService.registrarPedido(req));
    }

    // 3. Cantidades inválidas
    @Test
    @DisplayName("Escenario 3: Cantidad inválida (cero o negativa) rechazada")
    void testCantidadInvalida() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(clienteEjemplo));
        when(restauranteRepository.findById(1L)).thenReturn(Optional.of(restauranteEjemplo));

        List<PedidoService.ItemPedidoDto> items = List.of(
                new PedidoService.ItemPedidoDto("Pizza", 0, new BigDecimal("100.00"))
        );
        PedidoService.CrearPedidoRequest req = new PedidoService.CrearPedidoRequest(1L, 1L, items);
        assertThrows(BusinessRuleException.class, () -> pedidoService.registrarPedido(req));
    }

    // 5. Pago rechazado
    @Test
    @DisplayName("Escenario 5: Pago rechazado por la pasarela")
    void testPagoRechazado() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_CREADO, LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.findByPedidoId(1L)).thenReturn(List.of());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(i -> {
            Pago p = i.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(pasarelaPagoClient.procesarCobro(any())).thenReturn(
                new PasarelaPagoClient.CobroResponse(false, false, null, "Fondos insuficientes")
        );
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido actualizado = pedidoService.pagarPedido(1L, "TARJETA", "IDEM-1");
        assertEquals(PedidoService.ESTADO_PENDIENTE_PAGO, actualizado.getEstado());
        assertNull(actualizado.getFechaLimiteAceptacion());
    }

    // 6. Pago incierto / timeout
    @Test
    @DisplayName("Escenario 6: Pago incierto por timeout no inicia plazo de aceptación")
    void testPagoIncierto() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_CREADO, LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.findByPedidoId(1L)).thenReturn(List.of());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(i -> {
            Pago p = i.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(pasarelaPagoClient.procesarCobro(any())).thenReturn(
                new PasarelaPagoClient.CobroResponse(false, true, null, "Timeout en pasarela")
        );
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido actualizado = pedidoService.pagarPedido(1L, "TARJETA", "IDEM-1");
        assertEquals(PedidoService.ESTADO_PENDIENTE_PAGO, actualizado.getEstado());
        assertNull(actualizado.getFechaLimiteAceptacion());
    }

    // 7. Pago confirmado inicia plazo de 5 minutos
    @Test
    @DisplayName("Escenario 7: Pago confirmado formaliza inicio de plazo de aceptación de 5 min")
    void testPagoConfirmadoIniciaPlazo() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_CREADO, LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.findByPedidoId(1L)).thenReturn(List.of());
        when(pagoRepository.save(any(Pago.class))).thenAnswer(i -> {
            Pago p = i.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(pasarelaPagoClient.procesarCobro(any())).thenReturn(
                new PasarelaPagoClient.CobroResponse(true, false, "TX-999", "Aprobado")
        );
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido actualizado = pedidoService.pagarPedido(1L, "TARJETA", "IDEM-1");
        assertEquals(PedidoService.ESTADO_PENDIENTE_ACEPTACION, actualizado.getEstado());
        assertNotNull(actualizado.getFechaLimiteAceptacion());
        assertTrue(actualizado.getFechaLimiteAceptacion().isAfter(LocalDateTime.now().plusMinutes(4)));
    }

    // 8. Restaurante acepta dentro del plazo
    @Test
    @DisplayName("Escenario 8: Restaurante acepta a tiempo y se asigna repartidor")
    void testRestauranteAceptaDentroDelPlazo() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_PENDIENTE_ACEPTACION,
                LocalDateTime.now(), new BigDecimal("200.00"), null, LocalDateTime.now().plusMinutes(4));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_ACEPTADO)).thenReturn(1);
        when(repartidorRepository.findByDisponible(true)).thenReturn(new ArrayList<>(List.of(repartidorEjemplo)));
        when(repartidorRepository.updateDisponibilidadCondicional(repartidorEjemplo.getId(), true, false)).thenReturn(1);
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido aceptado = pedidoService.aceptarPedidoPorRestaurante(1L);
        assertNotNull(aceptado);
        verify(pedidoRepository).updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_ACEPTADO);
        verify(repartidorRepository).updateDisponibilidadCondicional(repartidorEjemplo.getId(), true, false);
    }

    // 9. Restaurante rechaza y solicita reembolso
    @Test
    @DisplayName("Escenario 9: Restaurante rechaza y se activa solicitud de reembolso")
    void testRestauranteRechaza() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_PENDIENTE_ACEPTACION,
                LocalDateTime.now(), new BigDecimal("200.00"), null, LocalDateTime.now().plusMinutes(4));
        Pago pagoCompletado = new Pago(10L, new BigDecimal("200.00"), LocalDateTime.now(), "TARJETA", pedido, PagoService.ESTADO_COMPLETADO);

        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_RECHAZADO)).thenReturn(1);
        when(pagoRepository.findByPedidoId(1L)).thenReturn(List.of(pagoCompletado));
        when(pagoRepository.findById(10L)).thenReturn(Optional.of(pagoCompletado));
        when(reembolsoRepository.findByPagoId(10L)).thenReturn(List.of());
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            Reembolso r = i.getArgument(0);
            r.setId(77L);
            return r;
        });
        when(pasarelaPagoClient.procesarReembolso(any())).thenReturn(
                new PasarelaPagoClient.ReembolsoResponse(true, false, "REF-123", "Reembolso OK")
        );

        Pedido resultado = pedidoService.rechazarPedidoPorRestaurante(1L, "Cocina saturada");
        assertNotNull(resultado);
        verify(pasarelaPagoClient).procesarReembolso(any());
    }

    // 10 & 11. Pedido vencido e intento de aceptación posterior
    @Test
    @DisplayName("Escenarios 10 y 11: Intento de aceptación después del vencimiento")
    void testIntentoAceptacionDespuesDeVencimiento() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_PENDIENTE_ACEPTACION,
                LocalDateTime.now().minusMinutes(10), new BigDecimal("200.00"), null, LocalDateTime.now().minusMinutes(5));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_CANCELADO)).thenReturn(1);

        assertThrows(BusinessRuleException.class, () -> pedidoService.aceptarPedidoPorRestaurante(1L));
        verify(pedidoRepository).updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_CANCELADO);
    }

    // 12. Aceptación concurrente con cancelación
    @Test
    @DisplayName("Escenario 12: Competencia concurrente entre aceptación y cancelación")
    void testAceptacionConcurrenteConCancelacion() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_PENDIENTE_ACEPTACION,
                LocalDateTime.now(), new BigDecimal("200.00"), null, LocalDateTime.now().plusMinutes(2));
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        // Simular que el scheduler ya canceló el registro en la base de datos
        when(pedidoRepository.updateEstadoCondicional(1L, PedidoService.ESTADO_PENDIENTE_ACEPTACION, PedidoService.ESTADO_ACEPTADO)).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> pedidoService.aceptarPedidoPorRestaurante(1L));
    }

    // 13. Reembolso con estados separados
    @Test
    @DisplayName("Escenario 13: Reembolso gestiona estados independientes")
    void testReembolsoIndependiente() {
        Pago pago = new Pago(1L, new BigDecimal("100.00"), LocalDateTime.now(), "TARJETA", null, PagoService.ESTADO_COMPLETADO);
        when(pagoRepository.findById(1L)).thenReturn(Optional.of(pago));
        when(reembolsoRepository.findByPagoId(1L)).thenReturn(List.of());
        when(reembolsoRepository.save(any(Reembolso.class))).thenAnswer(i -> {
            Reembolso r = i.getArgument(0);
            r.setId(5L);
            return r;
        });
        when(pasarelaPagoClient.procesarReembolso(any())).thenReturn(
                new PasarelaPagoClient.ReembolsoResponse(true, false, "REF-55", "OK")
        );

        Reembolso resultado = reembolsoService.solicitarReembolso(1L, "Cancelación");
        assertEquals(ReembolsoService.ESTADO_COMPLETADO, resultado.getEstado());
    }

    // 14. No hay repartidores disponibles
    @Test
    @DisplayName("Escenario 14: Pedido aceptado sin repartidores disponibles permanece sin cancelar")
    void testSinRepartidoresDisponibles() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_ACEPTADO,
                LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(repartidorRepository.findByDisponible(true)).thenReturn(List.of());

        Pedido resultado = entregaService.asignarRepartidorMasCercano(1L);
        assertEquals(PedidoService.ESTADO_ACEPTADO, resultado.getEstado());
        assertNull(resultado.getRepartidor());
    }

    // 15. Dos pedidos intentan reservar al mismo repartidor (bloqueo condicional)
    @Test
    @DisplayName("Escenario 15: Concurrencia de pedidos sobre mismo repartidor")
    void testConcurrenciaAsignacionRepartidor() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_ACEPTADO,
                LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(repartidorRepository.findByDisponible(true)).thenReturn(new ArrayList<>(List.of(repartidorEjemplo)));
        // Simular que otra transacción tomó el repartidor y el update devuelve 0
        when(repartidorRepository.updateDisponibilidadCondicional(repartidorEjemplo.getId(), true, false)).thenReturn(0);

        Pedido resultado = entregaService.asignarRepartidorMasCercano(1L);
        assertNull(resultado.getRepartidor()); // No se asigna indebidamente
    }

    // 16. Actualización de ubicación
    @Test
    @DisplayName("Escenario 16: Actualización válida de ubicación de repartidor")
    void testActualizacionUbicacion() {
        when(repartidorRepository.findById(1L)).thenReturn(Optional.of(repartidorEjemplo));
        when(repartidorRepository.save(any(Repartidor.class))).thenAnswer(i -> i.getArgument(0));

        Repartidor actualizado = repartidorService.actualizarUbicacion(1L, 19.4500, -99.1400);
        assertEquals(19.4500, actualizado.getLatitud());
        assertEquals(-99.1400, actualizado.getLongitud());
    }

    // 17. Entrega completada libera repartidor
    @Test
    @DisplayName("Escenario 17: Entrega completada libera al repartidor y asigna fecha")
    void testEntregaCompletada() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, repartidorEjemplo, PedidoService.ESTADO_EN_CAMINO,
                LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));

        Pedido entregado = entregaService.completarEntrega(1L);
        assertEquals(PedidoService.ESTADO_ENTREGADO, entregado.getEstado());
        assertNotNull(entregado.getFechaEntrega());
        verify(repartidorRepository).updateDisponibilidadCondicional(repartidorEjemplo.getId(), false, true);
    }

    // 18. Calificación válida y duplicada
    @Test
    @DisplayName("Escenario 18: Calificación sobre pedido entregado e impedimento de duplicados")
    void testCalificacionValidaYDuplicada() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, repartidorEjemplo, PedidoService.ESTADO_ENTREGADO,
                LocalDateTime.now(), new BigDecimal("200.00"), LocalDateTime.now(), null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(calificacionRepository.findByPedidoId(1L)).thenReturn(List.of());
        when(calificacionRepository.save(any(Calificacion.class))).thenAnswer(i -> {
            Calificacion c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        Calificacion c = calificacionService.registrarCalificacion(1L, 5, "Excelente entrega rápida");
        assertNotNull(c);
        assertEquals(5, c.getCalificacion());

        // Segunda llamada simulando que ya existe
        when(calificacionRepository.findByPedidoId(1L)).thenReturn(List.of(c));
        assertThrows(BusinessRuleException.class, () -> calificacionService.registrarCalificacion(1L, 4, "Otro comentario"));
    }

    // 19. Fallo en sistema de facturación legacy
    @Test
    @DisplayName("Escenario 19: Manejo resiliente ante indisponibilidad del sistema heredado 2012")
    void testFalloFacturacionLegacy() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_ENTREGADO,
                LocalDateTime.now(), new BigDecimal("200.00"), LocalDateTime.now(), null);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(detallePedidoRepository.findByPedidoId(1L)).thenReturn(List.of(
                new DetallePedido(1L, pedido, "Item 1", 1, new BigDecimal("200.00"))
        ));
        when(facturacionLegacyClient.emitirFactura(any())).thenReturn(
                new FacturacionLegacyClient.SolicitudFacturacionResponse(false, null, "Error 500 Legacy Core")
        );

        FacturacionLegacyClient.SolicitudFacturacionResponse resp = facturacionService.solicitarFacturacionPedido(1L);
        assertFalse(resp.exitoso());
        assertNotNull(resp.mensajeError());
    }

    // 20. Solicitudes repetidas de cobro (Idempotencia)
    @Test
    @DisplayName("Escenario 20: Idempotencia evita cobro repetido en pedidos ya pagados")
    void testIdempotenciaPago() {
        Pedido pedido = new Pedido(1L, clienteEjemplo, restauranteEjemplo, null, PedidoService.ESTADO_CREADO,
                LocalDateTime.now(), new BigDecimal("200.00"), null, null);
        Pago pagoExistente = new Pago(1L, new BigDecimal("200.00"), LocalDateTime.now(), "TARJETA", pedido, PagoService.ESTADO_COMPLETADO);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pagoRepository.findByPedidoId(1L)).thenReturn(List.of(pagoExistente));

        Pago resultado = pagoService.procesarPago(1L, "TARJETA", "IDEM-REPETIDO");
        assertEquals(1L, resultado.getId());
        assertEquals(PagoService.ESTADO_COMPLETADO, resultado.getEstado());
        verify(pasarelaPagoClient, never()).procesarCobro(any());
    }
}
