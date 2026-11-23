package Sistema.Reservas.Aulas.Aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/// Registra el nombre y el tiempo de ejecución de cada método de los servicios
/// Por seguridad NO se registran argumentos ni resultados: pueden contener correos, nombres, contraseñas
/// (en texto o en hash) y otros datos personales, y los datos escritos por el usuario podrían falsificar
/// líneas del log (inyección de saltos de línea).
@Aspect
@Component
public class LogAspect {

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Pointcut("execution(* Sistema.Reservas.Aulas.Service..*(..))")
    public void serviceMethods() {}

    @Around("serviceMethods()")
    public Object medirTiempo(ProceedingJoinPoint joinPoint) throws Throwable {
        long inicio = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            logger.debug("{}.{} -> {} ms",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    System.currentTimeMillis() - inicio);
        }
    }
}
