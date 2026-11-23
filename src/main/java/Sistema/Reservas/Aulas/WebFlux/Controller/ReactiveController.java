package Sistema.Reservas.Aulas.WebFlux.Controller;

import Sistema.Reservas.Aulas.WebFlux.Dto.BienvenidaResponse;
import Sistema.Reservas.Aulas.WebFlux.Dto.EventoReserva;
import Sistema.Reservas.Aulas.WebFlux.Dto.ResumenAulas;
import Sistema.Reservas.Aulas.WebFlux.Service.BienvenidaReactivaService;
import Sistema.Reservas.Aulas.WebFlux.Service.ConsultaAulasService;
import Sistema.Reservas.Aulas.WebFlux.Service.EventosReservaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Módulo WebFlux (/api/v1/reactive). El controlador solo recibe parámetros, los valida y devuelve
 * Mono o Flux; la lógica está en los servicios.
 *
 *   GET /api/v1/reactive/bienvenida/{usuario}                      Mono<BienvenidaResponse>
 *   GET /api/v1/reactive/reservas/stream?cantidad=6&intervaloMs=1000   Flux<EventoReserva> (text/event-stream)
 *   GET /api/v1/reactive/aulas/consulta?fuente=real                Mono<ResumenAulas> (WebClient)
 */
@RestController
@RequestMapping("/api/v1/reactive")
public class ReactiveController {

    private final BienvenidaReactivaService bienvenidaService;
    private final EventosReservaService eventosService;
    private final ConsultaAulasService consultaService;

    public ReactiveController(BienvenidaReactivaService bienvenidaService,
                              EventosReservaService eventosService,
                              ConsultaAulasService consultaService) {
        this.bienvenidaService = bienvenidaService;
        this.eventosService = eventosService;
        this.consultaService = consultaService;
    }

    /** Un único resultado: Mono. */
    @GetMapping("/bienvenida/{usuario}")
    public Mono<BienvenidaResponse> bienvenida(@PathVariable String usuario) {
        if (usuario.isBlank() || usuario.length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El usuario debe tener entre 1 y 60 caracteres");
        }
        return bienvenidaService.bienvenida(usuario.trim());
    }

    /** Streaming: Flux entregado como text/event-stream; cada evento llega apenas se emite. */
    @GetMapping(value = "/reservas/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<EventoReserva> streamReservas(
            @RequestParam(defaultValue = "6") int cantidad,
            @RequestParam(defaultValue = "1000") long intervaloMs) {
        if (cantidad < 5 || cantidad > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cantidad debe estar entre 5 y 20");
        }
        if (intervaloMs < 100 || intervaloMs > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "intervaloMs debe estar entre 100 y 5000");
        }
        return eventosService.flujoDeReservas(cantidad, Duration.ofMillis(intervaloMs));
    }

    /** Consumo de otra ruta HTTP con WebClient (con timeout y respaldo ante errores). */
    @GetMapping("/aulas/consulta")
    public Mono<ResumenAulas> consultarAulas(@RequestParam(defaultValue = "real") String fuente) {
        if (!ConsultaAulasService.FUENTES.containsKey(fuente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fuente no válida: use real, simulada, lenta, error o inexistente");
        }
        return consultaService.consultar(fuente);
    }
}
