package Sistema.Reservas.Aulas.Service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class ReservaReactivaService {

    public record ReservaReactiva(
            int id,
            String codigo,
            String aula,
            String solicitante,
            boolean activa,
            String estado
    ) { }

    private final List<ReservaReactiva> registros = List.of(
            new ReservaReactiva(1, "RES-001", "Aula 101", "Ana Gómez", true, "CONFIRMADA"),
            new ReservaReactiva(2, "RES-002", "Aula 202", "Carlos Ruiz", true, "CONFIRMADA"),
            new ReservaReactiva(3, "RES-003", "Laboratorio 1", "Laura Pérez", false, "CANCELADA"),
            new ReservaReactiva(4, "RES-004", "Aula 303", "Andrés López", true, "PENDIENTE")
    );

    /// Flux: emite los registros de reservas uno por uno.
    public Flux<ReservaReactiva> obtenerReservas() {
        return Flux.fromIterable(registros);
    }

    /// Mono: emite un único registro cuando existe el identificador solicitado.
    public Mono<ReservaReactiva> obtenerReservaPorId(int id) {
        return obtenerReservas()
                .filter(reserva -> reserva.id() == id)
                .next();
    }

    /// filter + map: conserva reservas activas y transforma su información para consola.
    public Flux<String> obtenerResumenesActivos() {
        return obtenerReservas()
                .filter(ReservaReactiva::activa)
                .map(reserva -> reserva.codigo() + " | " + reserva.aula()
                        + " | Solicitante: " + reserva.solicitante()
                        + " | Estado: " + reserva.estado());
    }

    /// count devuelve un Mono<long> con el número de reservas activas. </long>
    public Mono<Long> contarReservasActivas() {
        return obtenerReservas()
                .filter(ReservaReactiva::activa)
                .count();
    }

    /// Flujo vacío para comprobar el comportamiento cuando no hay coincidencias.
    public Flux<ReservaReactiva> obtenerReservasInactivasComoActivas() {
        return obtenerReservas()
                .filter(ReservaReactiva::activa)
                .filter(reserva -> reserva.estado().equals("CANCELADA"));
    }

    /** Emite una reserva cada 800 ms: en el navegador se ve cómo llegan una a una (streaming). */
    public Flux<ReservaReactiva> obtenerReservasEnVivo() {
        return obtenerReservas().delayElements(Duration.ofMillis(800));
    }

    /** Emite dos reservas y luego falla: sirve para mostrar la señal onError. */
    public Flux<ReservaReactiva> obtenerReservasConError() {
        return obtenerReservasEnVivo()
                .take(2)
                .concatWith(Flux.error(new IllegalStateException("Error simulado al consultar reservas")));
    }
}
