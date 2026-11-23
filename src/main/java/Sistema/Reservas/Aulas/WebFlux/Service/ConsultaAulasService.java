package Sistema.Reservas.Aulas.WebFlux.Service;

import Sistema.Reservas.Aulas.Dto.AulaSalida;
import Sistema.Reservas.Aulas.WebFlux.Config.FuenteProperties;
import Sistema.Reservas.Aulas.WebFlux.Dto.ResumenAulas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Consulta aulas a otra ruta HTTP con WebClient. Si la fuente falla, tarda demasiado o no existe,
 * responde con datos de respaldo en lugar de propagar el error.
 */
@Service
public class ConsultaAulasService {

    private static final Logger log = LoggerFactory.getLogger(ConsultaAulasService.class);

    /** fuente -> ruta HTTP. 'real' es la API de aulas del proyecto; las demás son la fuente local simulada. */
    public static final Map<String, String> FUENTES = Map.of(
            "real", "/api/aulas",
            "simulada", "/api/v1/reactive/simulado/aulas",
            "lenta", "/api/v1/reactive/simulado/lenta",
            "error", "/api/v1/reactive/simulado/error",
            "inexistente", "/api/v1/reactive/simulado/no-existe");

    private static final ParameterizedTypeReference<List<AulaSalida>> LISTA_AULAS = new ParameterizedTypeReference<>() { };

    private final WebClient webClient;
    private final Duration tiempoMaximo;

    public ConsultaAulasService(WebClient fuenteWebClient, FuenteProperties propiedades) {
        this.webClient = fuenteWebClient;
        this.tiempoMaximo = Duration.ofMillis(propiedades.timeoutMs());
    }

    public Mono<ResumenAulas> consultar(String fuente) {
        String ruta = FUENTES.get(fuente);
        return webClient.get()
                .uri(ruta)
                .retrieve()                                   // 4xx y 5xx se convierten en WebClientResponseException
                .bodyToMono(LISTA_AULAS)                      // el cuerpo JSON pasa a List<AulaSalida>
                .timeout(tiempoMaximo)                        // TimeoutException si no responde a tiempo
                .map(aulas -> new ResumenAulas(fuente, ruta, "fuente", true,
                        "Consulta exitosa: " + aulas.size() + " aulas recibidas de " + ruta,
                        aulas.size(), aulas))
                .onErrorResume(error -> Mono.just(respaldo(fuente, ruta, error)));
    }

    /** Datos locales que se devuelven cuando la fuente no responde bien. */
    private ResumenAulas respaldo(String fuente, String ruta, Throwable error) {
        log.warn("Fallo al consultar {}: {}", ruta, error.toString());
        List<AulaSalida> aulas = List.of(
                new AulaSalida(null, "RESPALDO-1", 30, "Sin conexión con la fuente", "Aula convencional", true),
                new AulaSalida(null, "RESPALDO-2", 20, "Sin conexión con la fuente", "Laboratorio", true),
                new AulaSalida(null, "RESPALDO-3", 50, "Sin conexión con la fuente", "Auditorio", true));
        return new ResumenAulas(fuente, ruta, "respaldo", false, describir(error), aulas.size(), aulas);
    }

    private String describir(Throwable error) {
        if (error instanceof TimeoutException) {
            return "La fuente no respondió en " + tiempoMaximo.toMillis() + " ms (timeout). Se usan datos de respaldo.";
        }
        if (error instanceof WebClientResponseException respuesta) {
            return "La fuente respondió HTTP " + respuesta.getStatusCode().value() + ". Se usan datos de respaldo.";
        }
        if (error instanceof WebClientRequestException) {
            return "No se pudo conectar con la fuente. Se usan datos de respaldo.";
        }
        return "Error inesperado al consultar la fuente (" + error.getClass().getSimpleName()
                + "). Se usan datos de respaldo.";
    }
}
