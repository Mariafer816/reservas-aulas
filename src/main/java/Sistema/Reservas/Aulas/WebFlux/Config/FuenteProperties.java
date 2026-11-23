package Sistema.Reservas.Aulas.WebFlux.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Propiedades webflux.fuente.* de application.properties. */
@ConfigurationProperties(prefix = "webflux.fuente")
public record FuenteProperties(
        @DefaultValue("http://localhost:8080") String url,
        @DefaultValue("2000") long timeoutMs) {
}
