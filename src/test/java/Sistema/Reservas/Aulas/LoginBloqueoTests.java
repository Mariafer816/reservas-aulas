package Sistema.Reservas.Aulas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/** Protección contra fuerza bruta: 5 contraseñas incorrectas seguidas bloquean la cuenta. */
@SpringBootTest
@AutoConfigureMockMvc
class LoginBloqueoTests {

    private static final String CLAVE = "Clave1234";

    @Autowired
    private MockMvc mockMvc;

    private void registrar(String correo) throws Exception {
        mockMvc.perform(post("/registro").with(csrf())
                .param("nombre", "Prueba Bloqueo")
                .param("correo", correo)
                .param("rol", "DOCENTE")
                .param("password", CLAVE)
                .param("confirmarPassword", CLAVE));
    }

    private void intentar(String correo, String clave, String destinoEsperado) throws Exception {
        mockMvc.perform(formLogin("/login").user("correo", correo).password("password", clave))
                .andExpect(redirectedUrl(destinoEsperado));
    }

    @Test
    void cincoFallosBloqueanLaCuentaAunqueLaClaveSeaCorrecta() throws Exception {
        String correo = "bloqueo.uno@etitc.edu.co";
        registrar(correo);

        for (int i = 0; i < 5; i++) {
            intentar(correo, "claveIncorrecta" + i, "/login?error");
        }
        // Con la contraseña CORRECTA también se rechaza mientras dure el bloqueo
        intentar(correo, CLAVE, "/login?error");
    }

    @Test
    void unInicioCorrectoReiniciaElContadorDeFallos() throws Exception {
        String correo = "bloqueo.dos@etitc.edu.co";
        registrar(correo);

        for (int i = 0; i < 4; i++) {
            intentar(correo, "claveIncorrecta" + i, "/login?error");
        }
        intentar(correo, CLAVE, "/");                        // entra y el contador vuelve a cero
        for (int i = 0; i < 4; i++) {
            intentar(correo, "otraIncorrecta" + i, "/login?error");
        }
        intentar(correo, CLAVE, "/");                        // 4 fallos no bastan para bloquear
    }

    @Test
    void elBloqueoDeUnCorreoNoAfectaAOtro() throws Exception {
        String bloqueado = "bloqueo.tres@etitc.edu.co";
        String libre = "bloqueo.cuatro@etitc.edu.co";
        registrar(bloqueado);
        registrar(libre);

        for (int i = 0; i < 5; i++) {
            intentar(bloqueado, "claveIncorrecta" + i, "/login?error");
        }
        intentar(libre, CLAVE, "/");
    }
}
