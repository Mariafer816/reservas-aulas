package Sistema.Reservas.Aulas.Service;

import Sistema.Reservas.Aulas.Model.Aula;
import Sistema.Reservas.Aulas.Model.Curso;
import Sistema.Reservas.Aulas.Model.Reserva;
import Sistema.Reservas.Aulas.Model.Usuario;
import Sistema.Reservas.Aulas.Repository.AulaRepository;
import Sistema.Reservas.Aulas.Repository.CursoRepository;
import Sistema.Reservas.Aulas.Repository.ReservaRepository;
import Sistema.Reservas.Aulas.Repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;



@Component
@Order(1)
@ConditionalOnProperty(name = "app.datos-iniciales", havingValue = "true", matchIfMissing = true)
public class DatosIniciales implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosIniciales.class);
    private static final String TIPO_BASE = "Aula convencional";

    private final AulaRepository aulaRepository;
    private final CursoRepository cursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaRepository reservaRepository;

    public DatosIniciales(AulaRepository aulaRepository, CursoRepository cursoRepository,
                          UsuarioRepository usuarioRepository, ReservaRepository reservaRepository) {
        this.aulaRepository = aulaRepository;
        this.cursoRepository = cursoRepository;
        this.usuarioRepository = usuarioRepository;
        this.reservaRepository = reservaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Aula> aulas = aulaRepository.count() == 0 ? cargarAulas() : aulaRepository.findAll();
        List<Curso> cursos = cursoRepository.count() == 0 ? cargarCursos() : cursoRepository.findAll();
        List<Usuario> usuarios = usuarioRepository.count() == 0 ? cargarUsuarios() : usuarioRepository.findAll();

        if (reservaRepository.count() == 0 && aulas.size() >= 9 && cursos.size() >= 10 && usuarios.size() >= 7) {
            cargarReservas(aulas, cursos, usuarios);
        }
        log.info("Datos predeterminados listos: {} aulas, {} cursos, {} usuarios, {} reservas",
                aulaRepository.count(), cursoRepository.count(), usuarioRepository.count(), reservaRepository.count());
    }

    private List<Aula> cargarAulas() {
        return aulaRepository.saveAll(List.of(
                new Aula(null, "E202", 30, "Edificio E", TIPO_BASE, true),
                new Aula(null, "E203", 25, "Edificio E", TIPO_BASE, true),
                new Aula(null, "E204", 40, "Edificio E", TIPO_BASE, true),
                new Aula(null, "E205", 35, "Edificio E", "Sala de informática", true),
                new Aula(null, "E206", 30, "Edificio E", TIPO_BASE, true),
                new Aula(null, "E207", 20, "Edificio E", "Laboratorio", true),
                new Aula(null, "E208", 50, "Edificio E", "Auditorio", true),
                new Aula(null, "E209", 45, "Edificio E", TIPO_BASE, true),
                new Aula(null, "E210", 30, "Edificio E", TIPO_BASE, true)));
    }

    private List<Curso> cargarCursos() {
        return cursoRepository.saveAll(List.of(
                new Curso("Programación Web", 64),
                new Curso("Bases de Datos", 64),
                new Curso("Estructuras de Datos", 48),
                new Curso("Ingeniería de Software", 48),
                new Curso("Redes de Computadores", 48),
                new Curso("Sistemas Operativos", 48),
                new Curso("Cálculo Diferencial", 64),
                new Curso("Álgebra Lineal", 48),
                new Curso("Inteligencia Artificial", 32),
                new Curso("Seguridad Informática", 32)));
    }

    private List<Usuario> cargarUsuarios() {
        return usuarioRepository.saveAll(List.of(
                new Usuario("Carlos Pérez", "carlos.perez@etitc.edu.co", "DOCENTE"),
                new Usuario("María Gómez", "maria.gomez@etitc.edu.co", "DOCENTE"),
                new Usuario("Luis Martínez", "luis.martinez@etitc.edu.co", "ESTUDIANTE"),
                new Usuario("Ana Rodríguez", "ana.rodriguez@etitc.edu.co", "ADMINISTRATIVO"),
                new Usuario("Jorge Ramírez", "jorge.ramirez@etitc.edu.co", "DOCENTE"),
                new Usuario("Laura Torres", "laura.torres@etitc.edu.co", "ESTUDIANTE"),
                new Usuario("Andrés Castillo", "andres.castillo@etitc.edu.co", "ESTUDIANTE")));
    }

    /** Seis reservas sin cruces de horario. La primera usa el aula, usuario y curso con id 1. */
    private void cargarReservas(List<Aula> aulas, List<Curso> cursos, List<Usuario> usuarios) {
        LocalDate hoy = LocalDate.now();
        reservaRepository.saveAll(List.of(
                reserva(aulas.get(0), usuarios.get(0), cursos.get(0), hoy.plusDays(1), 8, 10),   // E202 · Carlos · Programación Web
                reserva(aulas.get(0), usuarios.get(1), cursos.get(1), hoy.plusDays(1), 10, 12),  // E202 · María · Bases de Datos
                reserva(aulas.get(5), usuarios.get(4), cursos.get(4), hoy.plusDays(2), 14, 16),  // E207 · Jorge · Redes
                reserva(aulas.get(6), usuarios.get(3), cursos.get(3), hoy.plusDays(3), 9, 11),   // E208 · Ana · Ingeniería de Software
                reserva(aulas.get(3), usuarios.get(0), cursos.get(2), hoy.plusDays(3), 15, 17),  // E205 · Carlos · Estructuras de Datos
                reserva(aulas.get(2), usuarios.get(1), cursos.get(6), hoy.plusDays(4), 7, 9)));  // E204 · María · Cálculo Diferencial
    }

    private Reserva reserva(Aula aula, Usuario usuario, Curso curso, LocalDate fecha, int horaInicio, int horaFin) {
        Reserva r = new Reserva();
        r.setAula(aula);
        r.setUsuario(usuario);
        r.setCurso(curso);
        r.setFecha(fecha);
        r.setHoraInicio(LocalTime.of(horaInicio, 0));
        r.setHoraFin(LocalTime.of(horaFin, 0));
        return r;
    }
}
