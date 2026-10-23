package com.api.rapientrega.services;

import java.math.BigDecimal;

public interface PasarelaPagoClient {

    record CobroRequest(
            Long pedidoId,
            BigDecimal monto,
            String metodo,
            String referenciaCliente
    ) {}

    record CobroResponse(
            boolean exitoso,
            boolean incierto,
            String transaccionId,
            String mensaje
    ) {}

    record ReembolsoRequest(
            Long pagoId,
            String transaccionOriginalId,
            BigDecimal monto,
            String motivo
    ) {}

    record ReembolsoResponse(
            boolean exitoso,
            boolean incierto,
            String transaccionReembolsoId,
            String mensaje
    ) {}

    CobroResponse procesarCobro(CobroRequest request);

    ReembolsoResponse procesarReembolso(ReembolsoRequest request);
}
