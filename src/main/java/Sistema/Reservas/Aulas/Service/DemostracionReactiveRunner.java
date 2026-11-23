package Sistema.Reservas.Aulas.Service;

import Sistema.Reservas.Aulas.Service.ReservaReactivaService.ReservaReactiva;
import jakarta.annotation.Nonnull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.reactivestreams.Subscription;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo-reactiva-consola", havingValue = "true", matchIfMissing = true)
public class DemostracionReactiveRunner implements ApplicationRunner {

    private final ReservaReactivaService reservaReactivaService;

    public DemostracionReactiveRunner(ReservaReactivaService reservaReactivaService) {
        this.reservaReactivaService = reservaReactivaService;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("\n====================================================");
        System.out.println(" DEMOSTRACIÓN REACTIVA - SISTEMA DE RESERVAS DE AULAS");
        System.out.println("====================================================");

        demostrarFluxYOperadores();
        demostrarMono();
        demostrarFlujoSinResultados();
        demostrarSenales();
        demostrarControlDemanda();

        System.out.println("\n========== FIN DE LA DEMOSTRACIÓN REACTIVA ==========" + "\n");
    }

    private void demostrarFluxYOperadores() {
        System.out.println("\n[1] FLUX + FILTER + MAP: RESERVAS ACTIVAS");
        reservaReactivaService.obtenerResumenesActivos()
                .subscribe(
                        resumen -> System.out.println("onNext -> " + resumen),
                        error -> System.err.println("onError -> " + error.getMessage()),
                        () -> System.out.println("onComplete -> Reservas activas procesadas correctamente.")
                );
    }

    private void demostrarMono() {
        System.out.println("\n[2] MONO: CONTEO DE RESERVAS ACTIVAS");
        Mono<Long> totalActivas = reservaReactivaService.contarReservasActivas();
        totalActivas.subscribe(
                total -> System.out.println("Total de reservas activas: " + total),
                error -> System.err.println("Error al contar: " + error.getMessage()),
                () -> System.out.println("onComplete -> Conteo finalizado.")
        );

        System.out.println("\n[3] MONO: CONSULTA DE UNA RESERVA (ID 2)");
        reservaReactivaService.obtenerReservaPorId(2)
                .subscribe(
                        reserva -> System.out.println("Reserva encontrada: " + reserva.codigo()
                                + " - " + reserva.aula() + " - " + reserva.solicitante()),
                        error -> System.err.println("Error en consulta: " + error.getMessage()),
                        () -> System.out.println("Consulta individual finalizada.")
                );
    }

    private void demostrarFlujoSinResultados() {
        System.out.println("\n[4] CASO SIN RESERVAS ACTIVAS QUE CUMPLAN EL FILTRO");
        reservaReactivaService.obtenerReservasInactivasComoActivas()
                .switchIfEmpty(Flux.defer(() -> {
                    System.out.println("Sin resultados: no hay reservas activas con estado CANCELADA.");
                    return Flux.empty();
                }))
                .subscribe(
                        reserva -> System.out.println("Resultado: " + reserva.codigo()),
                        error -> System.err.println("onError -> " + error.getMessage()),
                        () -> System.out.println("onComplete -> Búsqueda sin coincidencias finalizada.")
                );
    }

    private void demostrarSenales() {
        System.out.println("\n[5] DEMOSTRACIÓN DE onNext, onComplete Y onError");
        Flux.just("Reserva RES-001 recibida", "Reserva RES-002 recibida")
                .subscribe(
                        dato -> System.out.println("onNext -> " + dato),
                        error -> System.err.println("onError -> " + error.getMessage()),
                        () -> System.out.println("onComplete -> Flujo terminado sin errores.")
                );

        Flux.<String>error(new IllegalStateException("Error simulado al consultar reservas"))
                .subscribe(
                        dato -> System.out.println("onNext -> " + dato),
                        error -> System.out.println("onError -> Error controlado: " + error.getMessage()),
                        () -> System.out.println("onComplete -> No se ejecuta cuando ocurre un error.")
                );
    }

    private void demostrarControlDemanda() {
        System.out.println("Se solicitará una reserva por vez y se cancelará después de recibir dos.");

        reservaReactivaService.obtenerReservas()
                .subscribe(new BaseSubscriber<ReservaReactiva>() {
                    private int recibidas = 0;

                    @Override
                    protected void hookOnSubscribe(@Nonnull Subscription subscription) {
                        request(1);
                    }

                    @Override
                    protected void hookOnNext(@Nonnull ReservaReactiva reserva) {
                        recibidas++;

                        if (recibidas == 2) {
                            cancel();
                        } else {
                            request(1);
                        }
                    }

                    @Override
                    protected void hookOnComplete() {
                        System.out.println("Flujo completado.");
                    }

                    @Override
                    protected void hookOnError(@Nonnull Throwable throwable) {
                        System.err.println("Error en BaseSubscriber: " + throwable.getMessage());
                    }

                    @Override
                    protected void hookOnCancel() {
                        System.out.println("Cancelación confirmada. No se reciben más reservas.");
                    }
                });
    }
}
