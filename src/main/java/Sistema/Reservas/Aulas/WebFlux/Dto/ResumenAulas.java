package Sistema.Reservas.Aulas.WebFlux.Dto;

import Sistema.Reservas.Aulas.Dto.AulaSalida;

import java.util.List;

/** Resultado de consultar aulas con WebClient: datos de la fuente o, si falla, los de respaldo. */
public record ResumenAulas(
        String fuente,        // real | simulada | lenta | error | inexistente
        String ruta,          // ruta HTTP consultada
        String origen,        // "fuente" o "respaldo"
        boolean exito,
        String mensaje,
        int total,
        List<AulaSalida> aulas) {
}
