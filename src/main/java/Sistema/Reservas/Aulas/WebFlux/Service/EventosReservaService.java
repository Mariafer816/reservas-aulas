package Sistema.Reservas.Aulas.WebFlux.Service;

import Sistema.Reservas.Aulas.Dto.ReservaSalida;
import Sistema.Reservas.Aulas.Service.ReservaService;
import Sistema.Reservas.Aulas.WebFlux.Dto.EventoReserva;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Monitor de reservas en vivo: emite un evento cada cierto tiempo con los datos de una reserva
 * (o un aviso si todavía no hay reservas registradas).
 */
@Service
public class EventosReservaService {

    private static final Logger log = LoggerFactory.getLogger(EventosReservaService.class);

    private final ReservaService reservaService;

    public EventosReservaService(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    /**
     * Flujo de exactamente {@code cantidad} eventos, uno cada {@code intervalo}:
     * interval() genera los pulsos, map() construye cada evento y take() corta el flujo.
     */
    public Flux<EventoReserva> flujoDeReservas(int cantidad, Duration intervalo) {
        return Mono.fromCallable(reservaService::consultarTodas)          // lectura bloqueante de la BD
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(reservas ->
                        Flux.interval(Duration.ZERO, intervalo)            // 0, 1, 2, ... cada intervalo
                                .map(i -> construirEvento(i.intValue() + 1, cantidad, reservas))
                                .take(cantidad))                           // corta tras 'cantidad' eventos
                .doOnCancel(() -> log.info("SSE cancelado: el navegador cerró la conexión"))
                .onErrorResume(error -> {                                  // un fallo se entrega como evento
                    log.warn("Error en el flujo de reservas", error);
                    return Flux.just(new EventoReserva(1, 1, "ERROR", hora(),
                            "No se pudo consultar las reservas: " + error.getClass().getSimpleName(),
                            null, null, null, null, true));
                });
    }

    private EventoReserva construirEvento(int secuencia, int total, List<ReservaSalida> reservas) {
        boolean ultimo = secuencia == total;
        if (reservas.isEmpty()) {
            return new EventoReserva(secuencia, total, "INFO", hora(),
                    "No hay reservas registradas (evento de seguimiento " + secuencia + " de " + total + ")",
                    null, null, null, null, ultimo);
        }
        ReservaSalida r = reservas.get((secuencia - 1) % reservas.size());   // recorre las reservas en ciclo
        return new EventoReserva(secuencia, total, "RESERVA", hora(),
                "Reserva " + r.id() + ": " + r.aulaNombre() + " para " + r.usuarioNombre()
                        + " el " + r.fecha() + " de " + r.horaInicio() + " a " + r.horaFin(),
                r.id(), r.aulaNombre(), r.usuarioNombre(), r.fecha(), ultimo);
    }

    private static String hora() {
        return LocalTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
