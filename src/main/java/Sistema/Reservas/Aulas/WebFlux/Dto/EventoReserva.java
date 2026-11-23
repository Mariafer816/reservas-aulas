package Sistema.Reservas.Aulas.WebFlux.Dto;

import java.time.LocalDate;

/**
 * Evento que se emite por SSE. El último evento del flujo lleva ultimo=true para que el navegador
 * sepa que terminó y cierre la conexión (si no, EventSource se reconectaría solo).
 */
public record EventoReserva(
        int secuencia,
        int total,
        String tipo,          // RESERVA | INFO | ERROR
        String hora,
        String mensaje,
        Long reservaId,
        String aula,
        String solicitante,
        LocalDate fecha,
        boolean ultimo) {
}
