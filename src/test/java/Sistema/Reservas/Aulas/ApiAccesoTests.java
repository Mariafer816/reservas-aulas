package Sistema.Reservas.Aulas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Con api.publica=true (por defecto) las rutas de datos solo son públicas desde este equipo (localhost).
 * Desde otra dirección exigen credenciales. Las rutas de demostración siguen abiertas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAccesoTests {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor desde(String direccion) {
        return peticion -> {
            peticion.setRemoteAddr(direccion);
            return peticion;
        };
    }

    @Test
    void desdeLocalhostLasRutasDeDatosSonPublicas() throws Exception {
        for (String ruta : new String[]{"/api/aulas", "/api/cursos", "/api/usuarios", "/api/reservas"}) {
            mockMvc.perform(get(ruta).with(desde("127.0.0.1"))).andExpect(status().isOk());
            mockMvc.perform(get(ruta).with(desde("0:0:0:0:0:0:0:1"))).andExpect(status().isOk());   // IPv6 local
        }
    }

    @Test
    void desdeOtraMaquinaLasRutasDeDatosExigenCredenciales() throws Exception {
        for (String ruta : new String[]{"/api/aulas", "/api/cursos", "/api/usuarios", "/api/reservas",
                "/api/v1/reactive/bienvenida/carlos", "/api/v1/reactive/aulas/consulta"}) {
            mockMvc.perform(get(ruta).with(desde("203.0.113.9"))).andExpect(status().isUnauthorized());
        }
        // El streaming de reservas reales también queda protegido
        mockMvc.perform(get("/api/v1/reactive/reservas/stream").with(desde("203.0.113.9")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lasRutasDeDemostracionSiguenAbiertasDesdeCualquierDireccion() throws Exception {
        mockMvc.perform(get("/api/reactivo/sin-resultados").with(desde("203.0.113.9"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/reactive/simulado/aulas").with(desde("203.0.113.9"))).andExpect(status().isOk());
    }

    @Test
    void laPaginaDeLoginEnviaCabecerasDeSeguridad() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
