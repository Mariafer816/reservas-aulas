# Sistema de Reservas de Aulas

Aplicación web para gestionar aulas, cursos, usuarios y reservas, con inicio de sesión y registro de cuentas.

**Stack:** Java 17 · Spring Boot 3.5 · Spring MVC + Thymeleaf · Spring Data JPA · Spring Security · H2 / MySQL 8

## Ejecución

Requisito: Java 17. **No hace falta instalar ninguna base de datos.**

1. Ejecute `./mvnw spring-boot:run` (en Windows: `mvnw.cmd spring-boot:run`) o ejecute `ReservasAulasApplication` desde IntelliJ.
   En IntelliJ, si ve dependencias en rojo: pestaña **Maven → Reload All Maven Projects**.
2. Espere el mensaje `Started ReservasAulasApplication in ... seconds`.
3. Abra http://localhost:8080 — le llevará al inicio de sesión.
4. Demostración reactiva (sin iniciar sesión): http://localhost:8080/reactivo

Flujo: **Login → Menú principal**. Si no tiene cuenta, el botón **Crear cuenta nueva** del login lleva al registro,
que crea la cuenta, inicia la sesión y entra directamente al menú.

### Base de datos SQL

| Modo | Cómo activarlo | Datos |
|---|---|---|
| **H2 en archivo** (por defecto) | No hace nada: ya viene configurado | Se **conservan** entre ejecuciones (carpeta `./data`) |
| **MySQL** | Perfil `mysql`: en IntelliJ, *Run > Edit Configurations > Active profiles = mysql*; por consola `-Dspring-boot.run.profiles=mysql` | Se conservan en MySQL |

Para MySQL: enciéndalo antes (por ejemplo desde XAMPP). La base `reservas_aulas` y las tablas se crean solas.
Si `root` tiene contraseña, defina la variable de entorno `DB_PASSWORD`.
El script `database/schema.sql` contiene el esquema MySQL equivalente por si se prefiere crearlo manualmente.

### Datos predeterminados
Al primer arranque se cargan datos de ejemplo (`Service/DatosIniciales.java`): **9 aulas** (E202–E210), **10 cursos**,
**7 usuarios** y **6 reservas** (con fechas relativas a hoy, sin cruces de horario). Cada tabla se llena solo si está vacía,
así que nunca se duplican ni se pisan datos existentes.

- Para no cargarlos: `app.datos-iniciales=false` en `application.properties`.
- Para empezar de cero con H2: detenga la aplicación y borre la carpeta `data`.
- Los usuarios predeterminados no tienen contraseña: cree la suya en `/registro` con uno de sus correos
  (por ejemplo `carlos.perez@etitc.edu.co`) y entrará con ese usuario.

## Si la aplicación no arranca

| Síntoma en la consola | Causa | Solución |
|---|---|---|
| `Communications link failure` / `Connection refused` | Está usando el perfil `mysql` y MySQL no está encendido | Encienda MySQL, o quite el perfil `mysql` para usar H2 |
| `Access denied for user 'root'` | MySQL tiene contraseña | Defina la variable `DB_PASSWORD` |
| `Port 8080 was already in use` | Otro programa usa el puerto | Cierre ese programa o cambie `server.port` en `application.properties` |
| `Database may be already in use` | Hay otra copia de la aplicación abierta | Detenga la otra ejecución (botón rojo de IntelliJ) |

## Inicio de sesión y registro

| Ruta         | Descripción                                                                    |
|--------------|--------------------------------------------------------------------------------|
| `/login`     | Ingreso con correo y contraseña; botón **Crear cuenta nueva**; al entrar lleva al menú |
| `/registro`  | Crea una cuenta nueva, **o** asigna contraseña a un usuario existente sin ella, e **inicia la sesión automáticamente** |
| `/logout`    | Cierra la sesión (botón en el menú)                                            |

- Las contraseñas se guardan con **BCrypt** en la columna `usuarios.password`; nunca en texto plano.
- Política: mínimo 8 caracteres, al menos una letra y un número.
- Los usuarios predeterminados (y los creados por la API) no tienen contraseña: la crean desde `/registro` con su correo.
- Un usuario creado por la API (`POST /api/usuarios`) no tiene contraseña: la crea desde `/registro` usando ese mismo correo.
- La API REST (`/api/**`) usa HTTP Basic con el mismo correo y contraseña (o sin credenciales si `api.publica=true`; ver sección **API REST**).

## API REST (servicios web)

CRUD en JSON de cuatro recursos, sobre la persistencia JPA de la Semana 13 (entidades y repositorios sin cambios).

| Recurso | Base | Controlador |
|---|---|---|
| Aulas | `/api/aulas` | `AulaController` |
| Cursos | `/api/cursos` | `CursoController` |
| Usuarios | `/api/usuarios` | `UsuarioController` |
| Reservas | `/api/reservas` | `ReservaController` |

### Arquitectura (separación de responsabilidades)

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Controller | `Controller` | Solo endpoints y respuestas HTTP (`ResponseEntity`). No contiene lógica |
| Service | `Service` | Operaciones del negocio y validaciones; lanza excepciones propias |
| Modelo de entrada | `Dto` (`*Entrada`) | Datos recibidos en POST/PUT, **sin identificador** (con `@Valid`) |
| Modelo de salida | `Dto` (`*Salida`, `ErrorRespuesta`) | Recurso devuelto, **con identificador** |
| Excepciones | `Excepcion` | `RecursoNoEncontradoException` (404) y `SolicitudInvalidaException` (400) |
| Errores HTTP | `Controller/ApiExceptionHandler` | Traduce excepciones a 400, 404 y 500 en JSON |
| Persistencia | `Model` (entidades), `Repository` | Sin cambios |

Las entidades JPA nunca se exponen: la API recibe `*Entrada` y devuelve `*Salida`. La contraseña de `Usuario` no existe en ningún modelo de la API.

### Acceso: público o con contraseña
Se controla con `api.publica` en `application.properties`:

- `api.publica=true` (por defecto): se prueba en Postman **sin cuenta ni contraseña** (Authorization = No Auth),
  pero **solo desde este mismo equipo** (`localhost`). Desde otra máquina de la red las rutas de datos responden 401 y exigen HTTP Basic.
- `api.publica=false`: las rutas de datos exigen siempre HTTP Basic (correo y contraseña de una cuenta creada en `/registro`).
  Úselo siempre que la aplicación quede accesible desde otros equipos o detrás de un proxy inverso.

Rutas de datos: `/api/aulas`, `/api/cursos`, `/api/usuarios`, `/api/reservas` y `/api/v1/reactive/**` (excepto `/simulado/**`).
Rutas siempre abiertas, porque solo devuelven datos de ejemplo: `/api/reactivo/**` y `/api/v1/reactive/simulado/**`.

### Endpoints y códigos HTTP

Los cuatro recursos tienen los mismos cinco endpoints:

| Método | Endpoint | Descripción | Éxito | Errores |
|---|---|---|---|---|
| GET | `/api/{recurso}` | Consultar todos | **200** | 500 |
| GET | `/api/{recurso}/{id}` | Consultar por ID | **200** | 400 (id no numérico), 404 |
| POST | `/api/{recurso}` | Crear | **201** + cabecera `Location` | 400 |
| PUT | `/api/{recurso}/{id}` | Actualizar | **200** | 400, 404 |
| DELETE | `/api/{recurso}/{id}` | Eliminar | **204** (sin contenido) | 400 (tiene reservas), 404 |

Reservas añade: `GET /api/reservas/fecha/{AAAA-MM-DD}` y `GET /api/reservas/aula/{aulaId}`.

| Código | Cuándo ocurre |
|---|---|
| 200 | Consulta o actualización exitosa |
| 201 | Recurso creado; `Location` apunta al nuevo recurso (p. ej. `http://localhost:8080/api/aulas/3`) |
| 204 | Eliminación exitosa, sin cuerpo |
| 400 | Campos inválidos, JSON mal formado, id o fecha con formato incorrecto, correo repetido, aula ocupada, o eliminar un registro con reservas |
| 404 | No existe un registro con ese id |
| 500 | Error interno no previsto: `{"estado":500,"mensaje":"Error interno del servidor"}` (el detalle queda solo en el log) |

### Modelos de entrada (POST y PUT, sin `id`)

| Recurso | Campos | Reglas |
|---|---|---|
| Aula | `nombre`, `capacidad`, `ubicacion`, `tipo`, `disponible` | nombre máx. 50; capacidad 1–500; ubicación máx. 100; tipo obligatorio; disponible opcional (por defecto `true`) |
| Curso | `nombre`, `horas` | nombre obligatorio, máx. 100; horas ≥ 1 |
| Usuario | `nombre`, `correo`, `rol` | correo válido y no repetido; rol = `DOCENTE`, `ESTUDIANTE` o `ADMINISTRATIVO` |
| Reserva | `aulaId`, `usuarioId`, `cursoId`, `fecha`, `horaInicio`, `horaFin` | todos obligatorios; fecha `AAAA-MM-DD` desde hoy; horas `HH:mm`; fin posterior al inicio; aula, usuario y curso deben existir; aula libre en ese horario |

Si se envía `id` en el cuerpo, se ignora: lo genera la base de datos.

### Modelos de salida (con `id`)

**201 — POST `/api/aulas`** (cabecera `Location: http://localhost:8080/api/aulas/1`)
```json
{ "id": 1, "nombre": "F101", "capacidad": 35, "ubicacion": "Edificio F", "tipo": "Laboratorio", "disponible": true }
```

**200 — GET `/api/reservas/1`**
```json
{
  "id": 1,
  "aulaId": 1, "aulaNombre": "F101",
  "usuarioId": 1, "usuarioNombre": "Sofia Herrera",
  "cursoId": 1, "cursoNombre": "Arquitectura de Software",
  "fecha": "2026-12-15", "horaInicio": "08:00:00", "horaFin": "10:00:00",
  "estado": "CONFIRMADA"
}
```

**204 — DELETE `/api/aulas/1`:** sin cuerpo.

**400 — datos inválidos**
```json
{
  "estado": 400,
  "mensaje": "Los datos enviados no son válidos",
  "errores": { "nombre": "El nombre del aula es obligatorio", "capacidad": "La capacidad debe ser al menos 1" }
}
```

**404 — recurso inexistente**
```json
{ "estado": 404, "mensaje": "No existe un aula con id 999999" }
```

**500 — error interno**
```json
{ "estado": 500, "mensaje": "Error interno del servidor" }
```
El 500 se comprueba con la prueba automática `ErrorInternoApiTests` (simula un fallo del servicio); no hay forma natural de provocarlo desde Postman con datos normales.

### Ver en localhost los cambios hechos desde Postman
La API y las páginas web usan **la misma base de datos**, y ya trae datos predeterminados.

1. Inicie la aplicación.
2. En el navegador: http://localhost:8080/registro con un correo predeterminado (por ejemplo `carlos.perez@etitc.edu.co`) y una contraseña. Entra al menú.
3. Deje abiertas **/aulas**, **/cursos**, **/usuarios** y **/reservas**; haga las peticiones en Postman y refresque (F5).
4. También puede ver el JSON en http://localhost:8080/api/aulas

Las listas web muestran 5 registros por página. Con los datos predeterminados, los ids `1` de aula, usuario y curso ya existen
(la reserva 1 los usa), así que puede crear reservas de inmediato y probar el 400 al eliminar un aula, curso o usuario con reservas.

### Pruebas en Postman
Cree cada petición a mano (método + URL; en POST y PUT: **Body → raw → JSON**) o, como opción, importe `postman/Reservas-API.postman_collection.json`.
Ciclo de sustentación: GET todos (200) → POST (201, revisar la cabecera **Location** en la pestaña *Headers* de la respuesta) → GET por id (200) → PUT (200) → DELETE (204) → GET (404) → errores 400.

```bash
curl -i http://localhost:8080/api/aulas
curl -i -X POST http://localhost:8080/api/cursos -H "Content-Type: application/json" -d '{"nombre":"Arquitectura de Software","horas":48}'
```

**Evidencias:** agregue aquí las capturas de Postman de cada paso, por ejemplo en `docs/evidencias/`.

## Seguridad y mantenimiento

### Medidas aplicadas

| Medida | Dónde | Qué evita |
|---|---|---|
| API de datos pública solo desde `localhost` | `SecurityConfig` | Que cualquier equipo de la red lea, cree o borre aulas, usuarios y reservas sin credenciales |
| Streaming y consultas reales de WebFlux protegidos igual | `SecurityConfig` | Que `/api/v1/reactive/**` exponga nombres, roles y reservas reales a desconocidos |
| Bloqueo tras 5 contraseñas incorrectas (15 min) | `Security/IntentosLogin` | Adivinar contraseñas por fuerza bruta, en el login y en HTTP Basic |
| `Content-Security-Policy`, `Referrer-Policy`, `X-Frame-Options` | `SecurityConfig` | XSS, clickjacking y filtración de URLs; por eso ya no hay `onclick`/`onsubmit` en el HTML (ahora `static/js/comun.js`) |
| Log sin argumentos ni resultados | `Aspect/LogAspect` | Que correos, nombres o contraseñas queden escritos en el log, y la falsificación de líneas de log |
| `spring.h2.console.enabled=false` | `application.properties` | Una consola web que permite ejecutar SQL (DevTools la activa por defecto) |
| `spring.jpa.show-sql=false` | `application.properties` | Ruido y datos en la consola |
| Cookie de sesión solo por cookie y `Secure` configurable | `application.properties` | Identificador de sesión en la URL; envío por HTTP cuando se publique con HTTPS (`COOKIE_SECURE=true`) |
| MySQL con `sslMode=PREFERRED`, sin `allowPublicKeyRetrieval` por defecto | `application-mysql.properties` | Conexión sin cifrar y robo de claves en tránsito |

### Limitaciones conocidas (no se corrigen porque requieren decisiones de diseño)

- **El registro es abierto:** quien conozca el correo de un usuario que aún no tiene contraseña (los predeterminados y los creados por la API)
  puede asignársela en `/registro`. Es el flujo del curso; en un sistema real haría falta verificar el correo.
- **Todos los usuarios tienen los mismos permisos** (`ROLE_USER`): el campo `rol` es informativo. Cualquier usuario con sesión
  puede editar o eliminar las reservas de otros. Para evitarlo habría que asignar reservas a su dueño y comprobar permisos.
- **Spring Boot 3.5.16 es la última versión con soporte gratuito de la línea 3.5** (junio de 2026). Funciona y está en Maven Central,
  pero ya no recibirá correcciones de seguridad; conviene planear el paso a Spring Boot 4.x como tarea aparte.

### Dependencias

Todas se resuelven desde Maven Central con las versiones que fija el `spring-boot-starter-parent` (ninguna lleva versión propia).
No se declaran `reactor-core` ni `spring-boot-starter-jdbc` porque ya vienen incluidos en `spring-boot-starter-webflux` y
`spring-boot-starter-data-jpa`. Si IntelliJ marca dependencias en rojo: pestaña **Maven → Reload All Maven Projects**.

### La carpeta `target`

`target/` la crea Maven al compilar (`target/classes` contiene los mismos paquetes `Sistema.Reservas.Aulas` ya compilados). No es
código fuente, está en `.gitignore` y no se sube al repositorio. Si IntelliJ la muestra como si fuera código duplicado:
clic derecho sobre `target` → **Mark Directory as → Excluded**, o bien `./mvnw clean` para borrarla (se regenera al compilar).

## Estructura del proyecto

```
src/main/java/Sistema/Reservas/Aulas
├── Aspect/        Registro (log) del tiempo de ejecución de los servicios (sin datos personales)
├── Controller/    Controladores web (Thymeleaf) y REST (/api); ModeloGlobalAdvice da tema y usuario activo a todas las vistas
├── Model/         Entidades JPA y formularios (CursoForm, ReservaForm, RegistroForm)
├── Repository/    Acceso a datos (Spring Data JPA)
├── Security/      Configuración de seguridad, login, registro y manejo de la HttpSession (entrada y salida)
├── Service/       Lógica de negocio
├── Util/          Utilidades (paginación, sesión)
├── Validation/    Validaciones propias reutilizables (@ContrasenaSegura, @PasswordsCoinciden)
├── WebFlux/       Módulo reactivo independiente: Controller, Service, Config y Dto (/api/v1/reactive)
└── ReservasAulasApplication.java

src/main/resources
├── templates/     Vistas Thymeleaf (login, registro, módulos) y fragments/componentes.html
├── static/css/    Hojas de estilo
└── application*.properties   Configuración (H2 en archivo por defecto; perfil mysql opcional)

database/schema.sql          Esquema SQL de MySQL
```

Flujo de capas: `Controller → Service → Repository → Base de datos SQL`.
`Security` agrupa lo relacionado con acceso; queda fuera de `Service` a propósito, porque `LogAspect` mide los
argumentos de los servicios y no deben aparecer contraseñas en el log.

## Pruebas

`./mvnw test` — usan H2 en memoria (`src/test/resources/application.properties`).

## Sesión, cookies y base de datos

Tres mecanismos distintos para guardar información. Cada uno responde a una pregunta diferente:

| | Sesión (`HttpSession`) | Cookie | Base de datos |
|---|---|---|---|
| **Dónde vive** | En el **servidor** (memoria) | En el **navegador** del usuario | En el **servidor** (MySQL), en disco |
| **Cuánto dura** | Hasta cerrar sesión o 30 min sin actividad | Lo que se defina (la de tema: 1 año) | Hasta que se borre |
| **Sobrevive a reiniciar el servidor** | No | Sí (está en el navegador) | Sí |
| **Quién puede alterarla** | Solo el servidor | El usuario puede editarla → **no es confiable** | Solo el servidor |
| **Sirve para** | "Quién está conectado ahora" | Preferencias simples de ese navegador | Datos del negocio: usuarios, aulas, reservas |
| **En este proyecto** | Nombre del usuario activo (`usuarioNombre`) | Tema visual (`tema`) y el id de sesión (`JSESSIONID`) | `usuarios`, `aulas`, `cursos`, `reservas` |

Cómo se conectan: la sesión **usa** una cookie. El servidor guarda los datos de la sesión y entrega al navegador solo
la cookie `JSESSIONID` con un identificador; en cada petición el navegador la devuelve y el servidor encuentra su sesión.

### Sesión (HttpSession)
- **Ingreso:** `LoginSuccessHandler` guarda el nombre con `session.setAttribute("usuarioNombre", ...)`.
- **Lectura:** `ModeloGlobalAdvice` lo recupera con `session.getAttribute(...)` y el menú lo muestra en todas las páginas.
- **Salida:** `CerrarSesionHandler` quita el atributo y ejecuta `session.invalidate()`; además se borra la cookie `JSESSIONID`.
- La sesión caduca tras 30 minutos sin actividad (`server.servlet.session.timeout`).

### Cookie de preferencias (tema visual)
- El botón **Tema oscuro / Tema claro** del menú envía `POST /preferencias/tema`.
- `PreferenciasController` → `PreferenciasService` crea la cookie `tema` (`HttpOnly`, `SameSite=Lax`, 1 año, `Secure` si hay HTTPS).
- En cada petición `ModeloGlobalAdvice` lee la cookie (`@CookieValue`) y, si vale `oscuro`, las páginas cargan `tema-oscuro.css`.
- La cookie **no se borra al cerrar sesión**: es una preferencia del navegador, no del acceso.
- Solo se aceptan los valores `claro` y `oscuro`; cualquier otro se descarta.

### Páginas de error
`templates/error/404.html` (página no encontrada), `templates/error/500.html` (error interno) y `templates/error.html`
(400, 403, 405 y demás). Todas muestran un mensaje claro, sin detalles técnicos, y el botón **Volver a la aplicación**.


## Componente reactivo (consola y navegador)

Demostración con **Project Reactor** (incluido en `spring-boot-starter-webflux`) integrada en Spring MVC; no toca la base de datos. (El módulo WebFlux con `WebClient` está descrito más abajo.)
Conserva la API REST y las páginas existentes.

### Archivos

- `Service/ReservaReactivaService.java`: cuatro reservas de ejemplo en memoria; `Flux`, `Mono`, `filter`, `map`, `count` y flujos "en vivo" con `delayElements`.
- `Service/DemostracionReactivaRunner.java`: ejecuta los ejemplos por **consola** al iniciar (se desactiva con `app.demo-reactiva-consola=false`).
- `Controller/ReactivoController.java`: endpoints `/api/reactivo/**` y el stream de eventos (SSE).
- `Controller/ReactivoWebController.java`, `templates/reactivo.html`, `static/js/reactivo.js`, `static/css/reactivo.css`: la **página del navegador**.

### Ver la demostración en el navegador
Con la aplicación iniciada, abra **http://localhost:8080/reactivo** (no pide iniciar sesión). Seis tarjetas, cada una con su botón:

| Tarjeta | Qué muestra |
|---|---|
| 1. Flux + filter + map | Las reservas activas, una línea `onNext` por cada una y `onComplete` al final (RES-003 se excluye) |
| 2. Mono | El conteo (`3`) y la consulta por id; con un id inexistente, Mono vacío (404) |
| 3. Flujo sin resultados | Un filtro sin coincidencias: termina con `onComplete` sin emitir elementos |
| 4. onNext y onComplete | Una reserva cada 800 ms llegando en vivo; el botón **Cancelar** cierra la conexión y el servidor cancela la suscripción |
| 5. onError | Dos reservas y luego un error simulado; no se ejecuta `onComplete` |
| 6. BaseSubscriber | `request(1)` por elemento y `cancel()` tras recibir dos |

### Endpoints reactivos (públicos, con Postman también sirven)

| Método | Endpoint | Respuesta |
|---|---|---|
| GET | `/api/reactivo/reservas` | 200, lista de las 4 reservas |
| GET | `/api/reactivo/reservas/{id}` | 200, o 404 si no existe |
| GET | `/api/reactivo/activas/resumen` | 200, textos de las reservas activas |
| GET | `/api/reactivo/activas/total` | 200, `{"total": 3}` |
| GET | `/api/reactivo/sin-resultados` | 200, `{"resultados": [], "mensaje": "..."}` |
| GET | `/api/reactivo/stream?modo=normal\|error\|demanda` | `text/event-stream` con eventos `onNext`, `onComplete`, `onError`, `request`, `cancel` |

### ¿Por qué se necesita subscribe()?

Los flujos `Flux` y `Mono` son publishers: describen qué operación debe realizarse, pero no comienzan a emitir datos por el solo hecho de declarar la cadena. La suscripción conecta un subscriber con el publisher y activa el procesamiento. A través de ella se reciben los elementos y las señales de terminación o error. En `BaseSubscriber`, además, se controla explícitamente cuántos elementos se solicitan y cuándo se cancela la suscripción. En la página web, `EventSource` es el suscriptor del navegador y cerrarlo equivale a `cancel()`.

### Alcance de los datos de ejemplo

Las cuatro reservas reactivas son datos en memoria, independientes de las reservas reales de la base de datos.

## Módulo WebFlux (`/api/v1/reactive`)

Módulo reactivo **independiente** del CRUD: vive en su propio paquete `Sistema.Reservas.Aulas.WebFlux` y no modifica el CRUD,
la API REST ni las pantallas anteriores. Se agregó la dependencia `spring-boot-starter-webflux` en el `pom.xml`.

> **Nota técnica:** el proyecto ya usa `spring-boot-starter-web` (Thymeleaf, Spring Security y JPA son de la pila servlet), y cuando ambos
> starters conviven Spring Boot arranca sobre **Tomcat con Spring MVC**. Los controladores del módulo devuelven `Mono` y `Flux`
> y Spring MVC los atiende de forma asíncrona (el streaming SSE funciona igual); `WebClient` es de WebFlux. Para ejecutar el módulo
> sobre Netty tendría que ser una aplicación aparte sin `spring-boot-starter-web`, y eso obligaría a migrar el resto.

### Organización

| Responsabilidad | Paquete | Clases |
|---|---|---|
| Controller | `WebFlux/Controller` | `ReactiveController` (los 3 endpoints), `FuenteSimuladaController` (fuente local simulada), `WebFluxPaginaController` (página `/webflux`) |
| Service | `WebFlux/Service` | `BienvenidaReactivaService` (Mono), `EventosReservaService` (Flux/SSE), `ConsultaAulasService` (WebClient) |
| Configuración | `WebFlux/Config` | `WebClientConfig` (bean `WebClient` con URL base), `FuenteProperties` (propiedades `webflux.fuente.*`) |
| Modelos | `WebFlux/Dto` | `BienvenidaResponse`, `EventoReserva`, `ResumenAulas` |

### Endpoints (públicos, no piden sesión)

| Método | Endpoint | Tipo | Descripción |
|---|---|---|---|
| GET | `/api/v1/reactive/bienvenida/{usuario}` | `Mono<BienvenidaResponse>` | Un único resultado: busca al usuario (por nombre o correo, sin distinguir tildes) y responde con su rol y su cantidad de reservas |
| GET | `/api/v1/reactive/reservas/stream?cantidad=6&intervaloMs=1000` | `Flux<EventoReserva>` | Streaming `text/event-stream`: un evento por intervalo, mínimo 5 (`cantidad` de 5 a 20, `intervaloMs` de 100 a 5000) |
| GET | `/api/v1/reactive/aulas/consulta?fuente=real` | `Mono<ResumenAulas>` | El servidor consulta otra ruta HTTP con `WebClient` |
| GET | `/api/v1/reactive/simulado/aulas`, `/lenta`, `/error` | `Mono` | Fuente local simulada: respuesta correcta, respuesta de 5 s y HTTP 500 |

**Mono** — `GET /api/v1/reactive/bienvenida/carlos`
```json
{
  "usuario": "carlos",
  "registrado": true,
  "nombre": "Carlos Pérez",
  "rol": "DOCENTE",
  "reservas": 2,
  "mensaje": "Bienvenido/a, Carlos Pérez (DOCENTE). Tiene 2 reservas registradas.",
  "fecha": "2026-11-17T10:30:00"
}
```
Un usuario que no existe también responde 200, con `"registrado": false`. Un usuario de más de 60 caracteres responde 400.

**Flux + SSE** — `EventosReservaService.flujoDeReservas`:
```java
Flux.interval(Duration.ZERO, intervalo)                 // un pulso por intervalo
    .map(i -> construirEvento(i + 1, cantidad, reservas))  // map(): cada pulso se transforma en un evento
    .take(cantidad)                                       // take(): el flujo termina tras 'cantidad' eventos
```
Los eventos se entregan progresivamente (cada uno llega apenas se emite, no todos al final). El último trae `"ultimo": true`.
Si el flujo falla, el error se entrega como un último evento de tipo `ERROR` (`onErrorResume`). Ejemplo de un evento:
```
data:{"secuencia":1,"total":6,"tipo":"RESERVA","hora":"10:30:01","mensaje":"Reserva 1: E202 para Carlos Pérez el 2026-11-18 de 08:00 a 10:00","reservaId":1,"aula":"E202","solicitante":"Carlos Pérez","fecha":"2026-11-18","ultimo":false}
```

**WebClient** — `ConsultaAulasService`:
```java
webClient.get().uri(ruta)
    .retrieve()                              // 4xx / 5xx -> WebClientResponseException
    .bodyToMono(LISTA_AULAS)                 // JSON -> List<AulaSalida>
    .timeout(tiempoMaximo)                   // timeout() -> TimeoutException
    .map(aulas -> /* resultado de la fuente */)
    .onErrorResume(error -> Mono.just(respaldo(...)));   // datos locales de respaldo
```
La URL base y el tiempo máximo se configuran en `application.properties`:
```properties
webflux.fuente.url=http://localhost:${server.port:8080}
webflux.fuente.timeout-ms=2000
```
Por defecto el WebClient consulta esta misma aplicación. Parámetro `fuente`:

| fuente | Ruta consultada | Resultado |
|---|---|---|
| `real` | `/api/aulas` (la API REST del proyecto) | `origen: "fuente"`, con las aulas reales |
| `simulada` | `/api/v1/reactive/simulado/aulas` | `origen: "fuente"`, 3 aulas simuladas |
| `lenta` | `/api/v1/reactive/simulado/lenta` (tarda 5 s) | timeout (2 s) → `origen: "respaldo"` |
| `error` | `/api/v1/reactive/simulado/error` (HTTP 500) | `origen: "respaldo"`, mensaje "La fuente respondió HTTP 500" |
| `inexistente` | `/api/v1/reactive/simulado/no-existe` (HTTP 404) | `origen: "respaldo"`, mensaje "La fuente respondió HTTP 404" |

Un error de la fuente **no** se propaga: la respuesta es 200 con `"exito": false` y datos de respaldo. Una `fuente` no válida responde 400.
Si `api.publica=false`, la fuente `real` devuelve 401 y también se usa el respaldo.

### Respuestas y errores

| Código | Cuándo |
|---|---|
| 200 | Consulta correcta, o consulta con respaldo por error de la fuente |
| 400 | `usuario` vacío o de más de 60 caracteres; `cantidad` o `intervaloMs` fuera de rango o no numéricos; `fuente` no válida |
| 404 | Ruta inexistente (por ejemplo la fuente simulada `no-existe`) |
| 500 | Error inesperado: `{"estado":500,"mensaje":"Error interno del servidor"}`; también la ruta simulada `/simulado/error` |

### Probar en el navegador y en Postman

- **Navegador:** http://localhost:8080/webflux (no pide sesión). Tres tarjetas, una por cada punto. En la del SSE se ve aparecer cada evento
  uno por uno; **Cancelar** cierra la conexión y el servidor cancela el flujo.
- **Directo en el navegador:** http://localhost:8080/api/v1/reactive/reservas/stream (los eventos aparecen en pantalla a medida que llegan).
- **Postman:** carpeta **WebFlux** de la colección (12 peticiones). Postman muestra los eventos del SSE al terminar el flujo o en tiempo real según su versión.
- **curl:** `curl -N http://localhost:8080/api/v1/reactive/reservas/stream` (`-N` desactiva el búfer: se ven los eventos uno por uno).

Pruebas automáticas: `WebFluxModuloTests` (Mono, SSE progresivo con `take`, WebClient con timeout, 404 y 500 contra un servidor HTTP falso).

