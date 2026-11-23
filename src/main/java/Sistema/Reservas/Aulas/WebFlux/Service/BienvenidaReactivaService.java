package Sistema.Reservas.Aulas.WebFlux.Service;

import Sistema.Reservas.Aulas.Model.Usuario;
import Sistema.Reservas.Aulas.Repository.ReservaRepository;
import Sistema.Reservas.Aulas.Repository.UsuarioRepository;
import Sistema.Reservas.Aulas.WebFlux.Dto.BienvenidaResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Bienvenida personalizada del sistema de reservas: busca al usuario por nombre o correo y
 * responde con su rol y su cantidad de reservas. Devuelve un único resultado (Mono).
 */
@Service
public class BienvenidaReactivaService {

    private final UsuarioRepository usuarioRepository;
    private final ReservaRepository reservaRepository;

    public BienvenidaReactivaService(UsuarioRepository usuarioRepository, ReservaRepository reservaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.reservaRepository = reservaRepository;
    }

    /**
     * La consulta a la base de datos (JPA, bloqueante) se ejecuta en el scheduler boundedElastic,
     * pensado para trabajo bloqueante, y el resultado se publica como un Mono.
     */
    public Mono<BienvenidaResponse> bienvenida(String usuario) {
        return Mono.fromCallable(() -> construir(usuario))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private BienvenidaResponse construir(String usuario) {
        Optional<Usuario> encontrado = buscar(usuario);
        if (encontrado.isEmpty()) {
            return new BienvenidaResponse(usuario, false, null, null, 0,
                    "Bienvenido/a, " + usuario + ". Todavía no está registrado en el sistema de reservas.",
                    LocalDateTime.now());
        }
        Usuario u = encontrado.get();
        long reservas = reservaRepository.countByUsuarioId(u.getId());
        String detalle = reservas == 1 ? "1 reserva registrada" : reservas + " reservas registradas";
        return new BienvenidaResponse(usuario, true, u.getNombre(), u.getRol(), reservas,
                "Bienvenido/a, " + u.getNombre() + " (" + u.getRol() + "). Tiene " + detalle + ".",
                LocalDateTime.now());
    }

    /** Coincidencia parcial en nombre o correo, sin distinguir mayúsculas ni tildes. */
    private Optional<Usuario> buscar(String texto) {
        String buscado = normalizar(texto);
        return usuarioRepository.findAll(Sort.by("nombre").ascending()).stream()
                .filter(u -> normalizar(u.getNombre()).contains(buscado)
                        || normalizar(u.getCorreo()).contains(buscado))
                .findFirst();
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }
}
