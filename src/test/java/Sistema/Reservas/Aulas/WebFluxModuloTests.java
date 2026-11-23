package Sistema.Reservas.Aulas;

import Sistema.Reservas.Aulas.Model.Usuario;
import Sistema.Reservas.Aulas.Repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Módulo WebFlux (/api/v1/reactive). El WebClient apunta a un servidor HTTP falso local que simula
 * la fuente externa: respuesta correcta, respuesta lenta (timeout) y error 500.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:webflux;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@AutoConfigureMockMvc
class WebFluxModuloTests {

    private static HttpServer fuente;

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) throws IOException {
        fuente = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fuente.setExecutor(Executors.newCachedThreadPool());
        responder("/api/aulas", 200, 0,
                "[{\"id\":1,\"nombre\":\"E202\",\"capacidad\":30,\"ubicacion\":\"Edificio E\",\"tipo\":\"Aula convencional\",\"disponible\":true},"
                        + "{\"id\":2,\"nombre\":\"E203\",\"capacidad\":25,\"ubicacion\":\"Edificio E\",\"tipo\":\"Laboratorio\",\"disponible\":true}]");
        responder("/api/v1/reactive/simulado/aulas", 200, 0, "[]");
        responder("/api/v1/reactive/simulado/lenta", 200, 3000, "[]");      // tarda más que el timeout (800 ms)
        responder("/api/v1/reactive/simulado/error", 500, 0, "{\"estado\":500}");
        fuente.start();
        registry.add("webflux.fuente.url", () -> "http://localhost:" + fuente.getAddress().getPort());
        registry.add("webflux.fuente.timeout-ms", () -> "800");
    }

    private static void responder(String ruta, int codigo, long demoraMs, String cuerpo) {
        fuente.createContext(ruta, intercambio -> {
            try {
                Thread.sleep(demoraMs);
                byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                intercambio.sendResponseHeaders(codigo, datos.length);
                intercambio.getResponseBody().write(datos);
            } catch (Exception ignorada) {
                // el cliente cortó la conexión (timeout): es lo esperado
            } finally {
                intercambio.close();
            }
        });
    }

    @AfterAll
    static void detener() {
        fuente.stop(0);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarios;

    /** Ejecuta la petición; si Spring la atendió de forma asíncrona (Mono/Flux), espera el resultado. */
    private MockHttpServletResponse ejecutar(String url) throws Exception {
        MvcResult r = mockMvc.perform(get(url)).andReturn();
        if (r.getRequest().isAsyncStarted()) {
            r = mockMvc.perform(asyncDispatch(r)).andReturn();
        }
        return r.getResponse();
    }

    private static String texto(MockHttpServletResponse resp) throws Exception {
        return resp.getContentAsString(StandardCharsets.UTF_8);
    }

    // ---------- 2. Mono ----------

    @Test
    void bienvenidaDeUsuarioRegistradoDevuelveUnSoloResultado() throws Exception {
        usuarios.save(new Usuario("Andrés Tester", "andres.tester@etitc.edu.co", "DOCENTE"));

        MockHttpServletResponse resp = ejecutar("/api/v1/reactive/bienvenida/andres");   // sin tilde y en minúscula
        assertEquals(200, resp.getStatus());
        String json = texto(resp);
        assertEquals(true, JsonPath.read(json, "$.registrado"));
        assertEquals("DOCENTE", JsonPath.read(json, "$.rol"));
        assertEquals(0, (int) JsonPath.read(json, "$.reservas"));
        assertTrue(((String) JsonPath.read(json, "$.mensaje")).contains("Bienvenido/a, Andrés Tester"));
    }

    @Test
    void bienvenidaDeUsuarioDesconocidoResponde200ConRegistradoFalso() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/v1/reactive/bienvenida/nadie-con-este-nombre");
        assertEquals(200, resp.getStatus());
        assertEquals(false, JsonPath.read(texto(resp), "$.registrado"));
    }

    @Test
    void bienvenidaConUsuarioDemasiadoLargoDevuelve400() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/v1/reactive/bienvenida/" + "x".repeat(61));
        assertEquals(400, resp.getStatus());
        assertEquals(400, (int) JsonPath.read(texto(resp), "$.estado"));
    }

    // ---------- 3. Flux + SSE ----------

    private static int contarEventos(MockHttpServletResponse resp) throws Exception {
        String t = texto(resp);
        return t.split("data:", -1).length - 1;
    }

    @Test
    void streamEmiteLosEventosUnoPorUnoYNoTodosAlFinal() throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/reactive/reservas/stream")
                        .param("cantidad", "5").param("intervaloMs", "400")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        MockHttpServletResponse resp = r.getResponse();

        Thread.sleep(150);
        int alInicio = contarEventos(resp);
        assertTrue(alInicio < 5, "Al inicio no deben haber llegado los 5 eventos, llegaron " + alInicio);

        long limite = System.currentTimeMillis() + 10_000;
        while (contarEventos(resp) < 5 && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }
        assertEquals(5, contarEventos(resp), "Debe emitir exactamente 5 eventos (take)");
        assertTrue(resp.getContentType().startsWith("text/event-stream"));
        assertTrue(texto(resp).contains("\"ultimo\":true"), "El último evento debe marcarse como ultimo");
    }

    @Test
    void streamConParametrosFueraDeRangoDevuelve400() throws Exception {
        mockMvc.perform(get("/api/v1/reactive/reservas/stream").param("cantidad", "3"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reactive/reservas/stream").param("intervaloMs", "10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reactive/reservas/stream").param("cantidad", "abc"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 4. WebClient ----------

    @Test
    void webClientConsultaLaFuenteYConvierteElJson() throws Exception {
        String json = texto(ejecutar("/api/v1/reactive/aulas/consulta?fuente=real"));
        assertEquals("fuente", JsonPath.read(json, "$.origen"));
        assertEquals(true, JsonPath.read(json, "$.exito"));
        assertEquals(2, (int) JsonPath.read(json, "$.total"));
        assertEquals("E202", JsonPath.read(json, "$.aulas[0].nombre"));
    }

    @Test
    void webClientConFuenteSimuladaResponde() throws Exception {
        String json = texto(ejecutar("/api/v1/reactive/aulas/consulta?fuente=simulada"));
        assertEquals(true, JsonPath.read(json, "$.exito"));
    }

    @Test
    void timeoutActivaElRespaldoConOnErrorResume() throws Exception {
        String json = texto(ejecutar("/api/v1/reactive/aulas/consulta?fuente=lenta"));
        assertEquals("respaldo", JsonPath.read(json, "$.origen"));
        assertEquals(false, JsonPath.read(json, "$.exito"));
        assertTrue(((String) JsonPath.read(json, "$.mensaje")).contains("timeout"));
        assertEquals(3, (int) JsonPath.read(json, "$.total"));
    }

    @Test
    void errorHttp500DeLaFuenteActivaElRespaldo() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/v1/reactive/aulas/consulta?fuente=error");
        assertEquals(200, resp.getStatus());   // el error de la fuente se maneja: no se propaga
        String json = texto(resp);
        assertEquals("respaldo", JsonPath.read(json, "$.origen"));
        assertTrue(((String) JsonPath.read(json, "$.mensaje")).contains("HTTP 500"));
    }

    @Test
    void errorHttp404DeLaFuenteActivaElRespaldo() throws Exception {
        String json = texto(ejecutar("/api/v1/reactive/aulas/consulta?fuente=inexistente"));
        assertEquals("respaldo", JsonPath.read(json, "$.origen"));
        assertTrue(((String) JsonPath.read(json, "$.mensaje")).contains("HTTP 404"));
    }

    @Test
    void fuenteNoValidaDevuelve400() throws Exception {
        MockHttpServletResponse resp = ejecutar("/api/v1/reactive/aulas/consulta?fuente=otra");
        assertEquals(400, resp.getStatus());
    }

    // ---------- fuente local simulada y página ----------

    @Test
    void rutasSimuladasDelPropioServidorSonPublicas() throws Exception {
        MockHttpServletResponse ok = ejecutar("/api/v1/reactive/simulado/aulas");
        assertEquals(200, ok.getStatus());
        assertEquals(3, (int) JsonPath.read(texto(ok), "$.length()"));

        MockHttpServletResponse fallo = ejecutar("/api/v1/reactive/simulado/error");
        assertEquals(500, fallo.getStatus());
        assertEquals(500, (int) JsonPath.read(texto(fallo), "$.estado"));
    }

    @Test
    void laPaginaWebFluxEsPublica() throws Exception {
        mockMvc.perform(get("/webflux")).andExpect(status().isOk());
        mockMvc.perform(get("/js/webflux.js")).andExpect(status().isOk());
    }
}
