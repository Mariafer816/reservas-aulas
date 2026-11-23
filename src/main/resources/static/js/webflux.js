// Módulo WebFlux: consume /api/v1/reactive/** y muestra cada respuesta o evento en su tarjeta.
(function () {
    'use strict';

    var stream = null;   // EventSource activo

    var linea = RxUtil.linea, limpiar = RxUtil.limpiar, pedir = RxUtil.pedir;

    // 1. Mono
    function bienvenida() {
        var s = 'salida-bienvenida';
        var usuario = document.getElementById('campo-usuario').value.trim();
        if (!usuario) { linea(s, 'Escriba un nombre o correo.', 'rx-info'); return; }
        linea(s, 'GET /api/v1/reactive/bienvenida/' + usuario, 'rx-info');
        pedir('/api/v1/reactive/bienvenida/' + encodeURIComponent(usuario)).then(function (r) {
            if (r.estado === 200) {
                linea(s, 'HTTP 200 -> ' + r.cuerpo.mensaje, r.cuerpo.registrado ? 'rx-onnext' : 'rx-info');
                linea(s, 'onComplete -> un único resultado (Mono).', 'rx-oncomplete');
            } else {
                linea(s, 'HTTP ' + r.estado + ' -> ' + (r.cuerpo && r.cuerpo.mensaje ? r.cuerpo.mensaje : 'error'), 'rx-onerror');
            }
        }).catch(function (e) { linea(s, 'No se pudo conectar: ' + e.message, 'rx-onerror'); });
    }

    // 2. Flux + SSE
    function iniciarStream() {
        var s = 'salida-stream';
        cancelar(true);
        limpiar(s);
        var cantidad = document.getElementById('campo-cantidad').value;
        var intervalo = document.getElementById('campo-intervalo').value;
        linea(s, 'Conectando con /api/v1/reactive/reservas/stream ...', 'rx-info');

        var es = new EventSource('/api/v1/reactive/reservas/stream?cantidad=' + encodeURIComponent(cantidad)
            + '&intervaloMs=' + encodeURIComponent(intervalo));
        stream = es;

        es.onmessage = function (e) {
            var ev;
            try { ev = JSON.parse(e.data); } catch (x) { return; }
            var clase = ev.tipo === 'ERROR' ? 'rx-onerror' : 'rx-onnext';
            linea(s, '(' + ev.secuencia + '/' + ev.total + ') ' + ev.mensaje, clase);
            if (ev.ultimo) {
                linea(s, ev.tipo === 'ERROR' ? 'Flujo terminado con error.' : 'onComplete -> take(' + ev.total + ') alcanzado, el servidor cierra el flujo.',
                    ev.tipo === 'ERROR' ? 'rx-onerror' : 'rx-oncomplete');
                es.close();
                stream = null;
            }
        };
        es.onerror = function () {
            if (stream === es) {
                linea(s, 'Conexión cerrada por el servidor.', 'rx-info');
                es.close();
                stream = null;
            }
        };
    }

    // Cancelar = cerrar la conexión: el servidor cancela el Flux y deja de emitir
    function cancelar(silencioso) {
        if (!stream) { return; }
        stream.close();
        stream = null;
        if (!silencioso) {
            linea('salida-stream', 'cancel() -> conexión cerrada; el servidor cancela la suscripción.', 'rx-cancel');
        }
    }

    // 3. WebClient
    function consultar() {
        var s = 'salida-consulta';
        var fuente = document.getElementById('campo-fuente').value;
        limpiar(s);
        linea(s, 'Consultando fuente "' + fuente + '" ...', 'rx-info');
        pedir('/api/v1/reactive/aulas/consulta?fuente=' + encodeURIComponent(fuente)).then(function (r) {
            if (r.estado !== 200) {
                linea(s, 'HTTP ' + r.estado + ' -> ' + (r.cuerpo && r.cuerpo.mensaje ? r.cuerpo.mensaje : 'error'), 'rx-onerror');
                return;
            }
            var d = r.cuerpo;
            linea(s, 'Ruta consultada: ' + d.ruta, 'rx-info');
            linea(s, (d.exito ? 'onNext' : 'onErrorResume') + ' -> ' + d.mensaje, d.exito ? 'rx-onnext' : 'rx-onerror');
            linea(s, 'Origen de los datos: ' + d.origen + ' (' + d.total + ' aulas)', 'rx-info');
            (d.aulas || []).forEach(function (a) {
                linea(s, a.nombre + ' | capacidad ' + a.capacidad + ' | ' + a.tipo, 'rx-info');
            });
        }).catch(function (e) { linea(s, 'No se pudo conectar: ' + e.message, 'rx-onerror'); });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.getElementById('btn-bienvenida').addEventListener('click', bienvenida);
        document.getElementById('btn-stream').addEventListener('click', iniciarStream);
        document.getElementById('btn-cancelar').addEventListener('click', function () { cancelar(false); });
        document.getElementById('btn-consulta').addEventListener('click', consultar);
        document.querySelectorAll('[data-limpiar]').forEach(function (b) {
            b.addEventListener('click', function () {
                if (b.dataset.limpiar === 'salida-stream') { cancelar(true); }
                limpiar(b.dataset.limpiar);
            });
        });
    });
})();
