package Sistema.Reservas.Aulas.Security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String API = "/api/**";

    // Rutas de la API que leen o modifican datos reales: CRUD y módulo WebFlux (bienvenida, streaming, consulta)
    private static final String[] API_DATOS = {
            "/api/aulas", "/api/aulas/**",
            "/api/cursos", "/api/cursos/**",
            "/api/usuarios", "/api/usuarios/**",
            "/api/reservas", "/api/reservas/**",
            "/api/v1/reactive/**"
    };

    // Rutas que solo devuelven datos de ejemplo (en memoria o simulados): siempre públicas
    private static final String[] API_DEMOSTRACION = {
            "/api/reactivo/**",
            "/api/v1/reactive/simulado/**"
    };

    private static final String LOGIN = "/login";
    private static final String REGISTRO = "/registro";
    private static final String LOGOUT = "/logout";

    /// Content-Security-Policy: el navegador solo ejecuta scripts y carga estilos, imágenes y conexiones del propio
    /// sitio. Si alguna vez se colara HTML malicioso (XSS), no podría ejecutar JavaScript ni enviar datos fuera.
    private static final String POLITICA_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; "
            + "base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /// API REST. Al no usar cookies de sesión no necesita token CSRF.
    /// - api.publica=true (por defecto): las rutas de datos se prueban en Postman sin credenciales, pero SOLO desde
    ///   este mismo equipo (localhost). Desde cualquier otra máquina exigen HTTP Basic (correo y contraseña).
    /// - api.publica=false: las rutas de datos exigen siempre HTTP Basic.
    /// - Detrás de un proxy inverso todas las peticiones parecen locales: allí use api.publica=false.
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurity(HttpSecurity http,
                                           @Value("${api.publica:true}") boolean apiPublica) throws Exception {
        http
                .securityMatcher(API)
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(API_DEMOSTRACION).permitAll();
                    if (apiPublica) {
                        auth.requestMatchers(API_DATOS).access(
                                SecurityConfig::localOAutenticado);
                    }
                    auth.anyRequest().authenticated();
                })
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Páginas web: todo requiere sesión salvo el login, el registro, las páginas de demostración y los estáticos. */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurity(HttpSecurity http,
                                           LoginSuccessHandler loginSuccessHandler,
                                           CerrarSesionHandler cerrarSesionHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(LOGIN, REGISTRO, "/error", "/css/**", "/js/**", "/reactivo", "/webflux").permitAll()
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(POLITICA_CSP))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
                .formLogin(form -> form
                        .loginPage(LOGIN)
                        .loginProcessingUrl(LOGIN)
                        .usernameParameter("correo")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)   // guarda el nombre en la HttpSession
                        .failureUrl(LOGIN + "?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT)              // POST + token CSRF (lo agrega Thymeleaf)
                        .addLogoutHandler(cerrarSesionHandler)   // invalida la HttpSession
                        .deleteCookies("JSESSIONID")             // borra la cookie de sesión del navegador
                        .logoutSuccessUrl(LOGIN + "?logout")
                        .permitAll());
        return http.build();
    }

    /// Permite la petición si viene de este mismo equipo (loopback) o si el usuario ya está autenticado.
    private static AuthorizationDecision localOAutenticado(Supplier<Authentication> autenticacion,
                                                           RequestAuthorizationContext contexto) {
        Authentication actual = autenticacion.get();
        boolean autenticado = actual != null && actual.isAuthenticated()
                && !(actual instanceof AnonymousAuthenticationToken);
        return new AuthorizationDecision(autenticado || esLocal(contexto.getRequest().getRemoteAddr()));
    }

    /// true para 127.0.0.1 y ::1. La dirección ya es numérica: no se hace ninguna consulta DNS.
    private static boolean esLocal(String direccion) {
        if (direccion == null || direccion.isBlank()) {
            return false;
        }
        try {
            return InetAddress.getByName(direccion).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
