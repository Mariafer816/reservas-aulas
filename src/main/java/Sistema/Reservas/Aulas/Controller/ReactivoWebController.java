package Sistema.Reservas.Aulas.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/// Página de la demostración reactiva: [...](http://localhost:8080/reactivo)
@Controller
public class ReactivoWebController {

    @GetMapping("/reactivo")
    public String pagina() {
        return "reactivo";
    }
}
