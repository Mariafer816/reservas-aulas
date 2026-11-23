package Sistema.Reservas.Aulas.WebFlux.Controller;

import Sistema.Reservas.Aulas.Dto.AulaSalida;
import Sistema.Reservas.Aulas.Dto.ErrorRespuesta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Fuente HTTP local simulada que consume el WebClient. Sirve para provocar a propósito
 * una respuesta correcta, una respuesta lenta (timeout) y un error 500.
 * (La ruta .../simulado/no-existe no se define: devuelve 404.)
 */
@RestController
@RequestMapping("/api/v1/reactive/simulado")
public class FuenteSimuladaController {

    /** Responde bien, con datos de ejemplo. */
    @GetMapping("/aulas")
    public Mono<List<AulaSalida>> aulas() {
        return Mono.just(List.of(
                new AulaSalida(101L, "SIM-A1", 30, "Edificio Simulado", "Aula convencional", true),
                new AulaSalida(102L, "SIM-B2", 20, "Edificio Simulado", "Laboratorio", true),
                new AulaSalida(103L, "SIM-C3", 60, "Edificio Simulado", "Auditorio", false)));
    }

    /** Tarda 5 segundos: el WebClient la corta por timeout. */
    @GetMapping("/lenta")
    public Mono<List<AulaSalida>> lenta() {
        return Mono.delay(Duration.ofSeconds(5)).map(ignorado -> List.of());
    }

    /** Falla con HTTP 500. */
    @GetMapping("/error")
    public Mono<ResponseEntity<ErrorRespuesta>> error() {
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorRespuesta.de(500, "Fallo simulado de la fuente")));
    }
}
