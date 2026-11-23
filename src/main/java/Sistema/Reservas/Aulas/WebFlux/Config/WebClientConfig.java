package Sistema.Reservas.Aulas.WebFlux.Config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/** Configuración del WebClient que consulta la fuente HTTP (URL base configurable). */
@Configuration
@EnableConfigurationProperties(FuenteProperties.class)
public class WebClientConfig {

    @Bean
    public WebClient fuenteWebClient(WebClient.Builder builder, FuenteProperties propiedades) {
        return builder
                .baseUrl(propiedades.url())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
