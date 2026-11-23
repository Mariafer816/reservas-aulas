package Sistema.Reservas.Aulas.Security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IntentosLogin {

    static final int MAX_INTENTOS = 5;
    static final int MINUTOS_BLOQUEO = 15;
    private static final Duration BLOQUEO = Duration.ofMinutes(MINUTOS_BLOQUEO);
    private static final int MAX_CORREOS_RECORDADOS = 10_000;

    private record Estado(int fallos, Instant bloqueadoHasta) { }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    public boolean estaBloqueado(String correo) {
        Estado estado = estados.get(clave(correo));
        if (estado == null || estado.bloqueadoHasta() == null) {
            return false;
        }
        if (Instant.now().isBefore(estado.bloqueadoHasta())) {
            return true;
        }
        estados.remove(clave(correo));   // el bloqueo ya venció
        return false;
    }

    @EventListener
    public void alFallar(AuthenticationFailureBadCredentialsEvent evento) {
        String correo = clave(evento.getAuthentication().getName());
        if (estados.size() >= MAX_CORREOS_RECORDADOS) {
            limpiarVencidos();
            if (estados.size() >= MAX_CORREOS_RECORDADOS && !estados.containsKey(correo)) {
                return;   // memoria llena de bloqueos vigentes: no se registran correos nuevos
            }
        }
        estados.merge(correo, new Estado(1, null), (actual, nuevo) -> {
            int fallos = actual.fallos() + 1;
            return new Estado(fallos, fallos >= MAX_INTENTOS ? Instant.now().plus(BLOQUEO) : null);
        });
    }

    @EventListener
    public void alIngresar(AuthenticationSuccessEvent evento) {
        estados.remove(clave(evento.getAuthentication().getName()));
    }

    private void limpiarVencidos() {
        Instant ahora = Instant.now();
        estados.entrySet().removeIf(e -> e.getValue().bloqueadoHasta() != null
                && !ahora.isBefore(e.getValue().bloqueadoHasta()));
    }

    private static String clave(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }
}
