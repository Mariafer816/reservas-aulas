package Sistema.Reservas.Aulas.Security;

import Sistema.Reservas.Aulas.Model.Usuario;
import Sistema.Reservas.Aulas.Repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;
    private final IntentosLogin intentosLogin;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository, IntentosLogin intentosLogin) {
        this.usuarioRepository = usuarioRepository;
        this.intentosLogin = intentosLogin;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        String buscado = correo == null ? "" : correo.trim();

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(buscado)
                .filter(u -> u.getPassword() != null)
                // Mensaje genérico a propósito: no revela si el correo existe o no
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword())
                .roles("USER")
                .accountLocked(intentosLogin.estaBloqueado(usuario.getCorreo()))   // demasiados intentos fallidos
                .build();
    }
}
