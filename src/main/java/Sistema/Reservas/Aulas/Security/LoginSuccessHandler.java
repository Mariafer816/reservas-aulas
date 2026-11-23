package Sistema.Reservas.Aulas.Security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    public static final String ATRIBUTO_NOMBRE = "usuarioNombre";

    private final GestorSesion gestorSesion;

    public LoginSuccessHandler(GestorSesion gestorSesion) {
        this.gestorSesion = gestorSesion;
        setDefaultTargetUrl("/");
        setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        gestorSesion.guardarNombre(request, authentication.getName());

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
