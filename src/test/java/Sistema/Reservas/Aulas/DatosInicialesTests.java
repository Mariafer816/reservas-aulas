package Sistema.Reservas.Aulas;

import Sistema.Reservas.Aulas.Repository.AulaRepository;
import Sistema.Reservas.Aulas.Repository.CursoRepository;
import Sistema.Reservas.Aulas.Repository.ReservaRepository;
import Sistema.Reservas.Aulas.Repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Con app.datos-iniciales=true la base arranca con aulas, cursos, usuarios y reservas. */
@SpringBootTest(properties = {
        "app.datos-iniciales=true",
        "spring.datasource.url=jdbc:h2:mem:semillas;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"   // base aislada de las demás pruebas
})
@AutoConfigureMockMvc
class DatosInicialesTests {

    @Autowired private AulaRepository aulas;
    @Autowired private CursoRepository cursos;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private ReservaRepository reservas;
    @Autowired private MockMvc mockMvc;

    @Test
    void laBaseArrancaConDatosPredeterminados() {
        assertEquals(9, aulas.count());
        assertEquals(10, cursos.count());
        assertEquals(7, usuarios.count());
        assertEquals(6, reservas.count());
    }

    @Test
    void laApiYaDevuelveLosDatosSinCrearNada() throws Exception {
        mockMvc.perform(get("/api/aulas")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(9));
        mockMvc.perform(get("/api/cursos")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
        mockMvc.perform(get("/api/usuarios")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(7));
        mockMvc.perform(get("/api/reservas")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void laPrimeraReservaUsaElAulaUsuarioYCursoConIdUno() throws Exception {
        mockMvc.perform(get("/api/reservas/aula/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].aulaNombre").value("E202"));
    }
}
