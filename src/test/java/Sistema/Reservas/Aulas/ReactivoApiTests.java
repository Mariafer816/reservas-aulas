package Sistema.Reservas.Aulas;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints reactivos de /api/reactivo (públicos, sin base de datos). */
@SpringBootTest
@AutoConfigureMockMvc
class ReactivoApiTests {

    @Autowired
    private MockMvc mockMvc;

    /** Ejecuta la petición; si Spring la atendió de forma asíncrona (Mono/Flux), espera el resultado. */
    private MockHttpServletResponse ejecutar(String url) throws Exception {
        MvcResult r = mockMvc.perform(get(url)).andReturn();
        if (r.getRequest().isAsyncStarted()) {
            r = mockMvc.perform(asyncDispatch(r)).andReturn();
        }
        return r.getResponse();
    }

    @Test
    void fluxDevuelveLasCuatroReservasSinCredenciales() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/reactivo/reservas");
        assertEquals(200, resp.getStatus());
        assertEquals(4, (int) JsonPath.read(resp.getContentAsString(), "$.length()"));
    }

    @Test
    void monoDevuelveLaReservaOEl404() throws Exception {
        MockHttpServletResponse ok = ejecutar("/api/reactivo/reservas/2");
        assertEquals(200, ok.getStatus());
        assertEquals("RES-002", JsonPath.read(ok.getContentAsString(), "$.codigo"));

        MockHttpServletResponse noExiste = ejecutar("/api/reactivo/reservas/99");
        assertEquals(404, noExiste.getStatus());
        assertEquals(404, (int) JsonPath.read(noExiste.getContentAsString(), "$.estado"));
    }

    @Test
    void filterMapYCountSoloConsideranLasActivas() throws Exception {
        MockHttpServletResponse resumen = ejecutar("/api/reactivo/activas/resumen");
        assertEquals(3, (int) JsonPath.read(resumen.getContentAsString(), "$.length()"));

        MockHttpServletResponse total = ejecutar("/api/reactivo/activas/total");
        assertEquals(3, (int) JsonPath.read(total.getContentAsString(), "$.total"));
    }

    @Test
    void flujoVacioTerminaSinElementos() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/reactivo/sin-resultados");
        assertEquals(200, resp.getStatus());
        assertEquals(0, (int) JsonPath.read(resp.getContentAsString(), "$.resultados.length()"));
    }

    @Test
    void laPaginaReactivaEsPublica() throws Exception {
        mockMvc.perform(get("/reactivo")).andExpect(status().isOk());
        assertTrue(mockMvc.perform(get("/js/reactivo.js")).andReturn().getResponse().getStatus() == 200);
    }

    @Test
    void streamConModoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/reactivo/stream").param("modo", "otro")).andExpect(status().isBadRequest());
    }
}
