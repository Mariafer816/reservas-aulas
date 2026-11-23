package Sistema.Reservas.Aulas.WebFlux.Dto;

import java.time.LocalDateTime;

/** Resultado único de GET /api/v1/reactive/bienvenida/{usuario}. */
public record BienvenidaResponse(
        String usuario,
        boolean registrado,
        String nombre,
        String rol,
        long reservas,
        String mensaje,
        LocalDateTime fecha) {
}
