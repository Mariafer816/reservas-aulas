package Sistema.Reservas.Aulas.WebFlux.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Página para probar el módulo WebFlux desde el navegador: http://localhost:8080/webflux */
@Controller
public class WebFluxPaginaController {

    @GetMapping("/webflux")
    public String pagina() {
        return "webflux";
    }
}
