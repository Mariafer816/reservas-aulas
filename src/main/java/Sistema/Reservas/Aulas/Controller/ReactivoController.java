package Sistema.Reservas.Aulas.Controller;

import Sistema.Reservas.Aulas.Dto.ErrorRespuesta;
import Sistema.Reservas.Aulas.Service.ReservaReactivaService;
import Sistema.Reservas.Aulas.Service.ReservaReactivaService.ReservaReactiva;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/// Demostración reactiva en el navegador (/reactivo). Usa reactor-core dentro de Spring MVC: no requiere WebFlux
/// y no toca la base de datos (los datos son los de ReservaReactivaService).
///   GET /api/reactivo/reservas              Flux  -> lista JSON
///   GET /api/reactivo/reservas/{id}         Mono  -> 200 o 404
///   GET /api/reactivo/activas/resumen       Flux + filter + map
///   GET /api/reactivo/activas/total         Mono<long> (count)
///   GET /api/reactivo/sin-resultados        flujo vacío
///   GET /api/reactivo/stream?modo=...       SSE: señales onNext / onComplete / onError / request / cancel</long>
@RestController
@RequestMapping("/api/reactivo")
public class ReactivoController {

    private static final Logger log = LoggerFactory.getLogger(ReactivoController.class);
    private static final Set<String> MODOS = Set.of("normal", "error", "demanda");

    private final ReservaReactivaService servicio;

    public ReactivoController(ReservaReactivaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/reservas")
    public Flux<ReservaReactiva> reservas() {
        return servicio.obtenerReservas();
    }

    @GetMapping("/reservas/{id}")
    public Mono<ResponseEntity<Object>> reserva(@PathVariable int id) {
        return servicio.obtenerReservaPorId(id)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorRespuesta.de(404, "No existe una reserva reactiva con id " + id)));
    }

    @GetMapping("/activas/resumen")
    public Mono<List<String>> resumenesActivos() {
        return servicio.obtenerResumenesActivos().collectList();
    }

    @GetMapping("/activas/total")
    public Mono<Map<String, Long>> totalActivas() {
        return servicio.contarReservasActivas().map(total -> Map.of("total", total));
    }

    @GetMapping("/sin-resultados")
    public Mono<Map<String, Object>> sinResultados() {
        return servicio.obtenerReservasInactivasComoActivas()
                .collectList()
                .map(lista -> Map.<String, Object>of(
                        "resultados", lista,
                        "mensaje", lista.isEmpty()
                                ? "Sin resultados: no hay reservas activas con estado CANCELADA."
                                : "Se encontraron " + lista.size() + " reservas"));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(defaultValue = "normal") String modo) {
        if (!MODOS.contains(modo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modo no válido: use normal, error o demanda");
        }
        SseEmitter emisor = new SseEmitter(60_000L);

        if ("demanda".equals(modo)) {
            Disposable suscripcion = suscribirConDemanda(servicio.obtenerReservasEnVivo(), emisor);
            enlazarCancelacion(emisor, suscripcion);
        } else {
            Flux<ReservaReactiva> flujo = "error".equals(modo)
                    ? servicio.obtenerReservasConError()
                    : servicio.obtenerReservasEnVivo();
            Disposable suscripcion = flujo
                    .doOnCancel(() -> log.info("cancel(): el navegador cerró la conexión, se cancela la suscripción"))
                    .subscribe(
                            reserva -> enviar(emisor, "onNext", reserva),
                            error -> {
                                enviar(emisor, "onError", Map.of("mensaje", String.valueOf(error.getMessage())));
                                emisor.complete();
                            },
                            () -> {
                                enviar(emisor, "onComplete", Map.of("mensaje", "Flujo terminado sin errores"));
                                emisor.complete();
                            });
            enlazarCancelacion(emisor, suscripcion);
        }
        return emisor;
    }

    /** Subscriber que pide de a un elemento (request(1)) y se cancela al recibir el segundo. */
    private Disposable suscribirConDemanda(Flux<ReservaReactiva> flujo, SseEmitter emisor) {
        BaseSubscriber<ReservaReactiva> subscriber = new BaseSubscriber<>() {
            private int recibidas = 0;

            @Override
            protected void hookOnSubscribe(@Nonnull org.reactivestreams.Subscription subscription) {
                enviar(emisor, "onSubscribe", Map.of("mensaje", "Suscripción iniciada"));
                enviar(emisor, "request", Map.of("n", 1));
                request(1);
            }

            @Override
            protected void hookOnNext(@Nonnull ReservaReactiva reserva) {
                recibidas++;
                enviar(emisor, "onNext", reserva);
                if (recibidas == 2) {
                    enviar(emisor, "cancel", Map.of("mensaje", "cancel(): no se solicitarán ni recibirán más elementos"));
                    cancel();
                    emisor.complete();
                } else {
                    enviar(emisor, "request", Map.of("n", 1));
                    request(1);
                }
            }

            @Override
            protected void hookOnComplete() {
                enviar(emisor, "onComplete", Map.of("mensaje", "Flujo terminado sin errores"));
                emisor.complete();
            }

            @Override
            protected void hookOnError(Throwable error) {
                enviar(emisor, "onError", Map.of("mensaje", String.valueOf(error.getMessage())));
                emisor.complete();
            }
        };
        flujo.subscribe(subscriber);
        return subscriber;
    }

    /** Si la conexión se cierra o vence, se cancela la suscripción para no seguir emitiendo en vano. */
    private void enlazarCancelacion(SseEmitter emisor, Disposable suscripcion) {
        emisor.onCompletion(suscripcion::dispose);
        emisor.onTimeout(suscripcion::dispose);
        emisor.onError(error -> suscripcion.dispose());
    }

    /** Envía un evento SSE con el dato en JSON; si el navegador ya cerró la conexión, se ignora. */
    private void enviar(SseEmitter emisor, String evento, Object dato) {
        try {
            emisor.send(SseEmitter.event().name(evento).data(dato, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            log.debug("No se pudo enviar el evento '{}': la conexión ya está cerrada", evento);
        }
    }
}
