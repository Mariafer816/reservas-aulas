// Demostración reactiva: consume /api/reactivo/** y muestra cada señal en la tarjeta correspondiente.
(function () {
    'use strict';

    var linea = RxUtil.linea, limpiar = RxUtil.limpiar, pedir = RxUtil.pedir;

    var streams = {};   // salidaId -> EventSource activo

    function describir(r) {
        return r.codigo + ' | ' + r.aula + ' | ' + r.solicitante + ' | ' + r.estado;
    }


    function falloDeRed(salida, error) {
        linea(salida, 'onError -> No se pudo conectar con el servidor: ' + error.message, 'rx-onerror');
    }

    // 1. Flux + filter + map
    function ejecutarFlux() {
        var s = 'salida-flux';
        limpiar(s);
        linea(s, 'subscribe() -> pide las reservas activas', 'rx-info');
        pedir('/api/reactivo/activas/resumen').then(function (r) {
            (r.cuerpo || []).forEach(function (resumen) { linea(s, 'onNext -> ' + resumen, 'rx-onnext'); });
            linea(s, 'onComplete -> Reservas activas procesadas correctamente.', 'rx-oncomplete');
        }).catch(function (e) { falloDeRed(s, e); });
    }

    // 2. Mono
    function contarActivas() {
        var s = 'salida-mono';
        pedir('/api/reactivo/activas/total').then(function (r) {
            linea(s, 'onNext -> Total de reservas activas: ' + r.cuerpo.total, 'rx-onnext');
            linea(s, 'onComplete -> Conteo finalizado.', 'rx-oncomplete');
        }).catch(function (e) { falloDeRed(s, e); });
    }

    function consultarUna() {
        var s = 'salida-mono';
        var id = document.getElementById('campo-id').value;
        if (!id) { linea(s, 'Escriba un id.', 'rx-info'); return; }
        pedir('/api/reactivo/reservas/' + encodeURIComponent(id)).then(function (r) {
            if (r.estado === 200) {
                linea(s, 'onNext -> Reserva encontrada: ' + describir(r.cuerpo), 'rx-onnext');
            } else {
                linea(s, 'Mono vacío (HTTP ' + r.estado + ') -> ' + (r.cuerpo && r.cuerpo.mensaje ? r.cuerpo.mensaje : 'no encontrada'), 'rx-onerror');
            }
            linea(s, 'onComplete -> Consulta individual finalizada.', 'rx-oncomplete');
        }).catch(function (e) { falloDeRed(s, e); });
    }

    // 3. Flujo vacío
    function ejecutarVacio() {
        var s = 'salida-vacio';
        limpiar(s);
        pedir('/api/reactivo/sin-resultados').then(function (r) {
            linea(s, r.cuerpo.mensaje, 'rx-info');
            linea(s, 'onComplete -> Búsqueda sin coincidencias finalizada (0 elementos).', 'rx-oncomplete');
        }).catch(function (e) { falloDeRed(s, e); });
    }

    // 4-6. Server-Sent Events: cada evento del servidor es una señal del Flux
    function iniciarStream(modo, salidaId) {
        cancelar(salidaId, true);
        limpiar(salidaId);
        linea(salidaId, 'subscribe() -> conectando con /api/reactivo/stream?modo=' + modo, 'rx-info');

        var es = new EventSource('/api/reactivo/stream?modo=' + encodeURIComponent(modo));
        streams[salidaId] = es;

        function dato(e) { try { return JSON.parse(e.data); } catch (x) { return {}; } }
        function cerrar() { es.close(); delete streams[salidaId]; }

        es.addEventListener('onSubscribe', function (e) { linea(salidaId, 'onSubscribe -> ' + dato(e).mensaje, 'rx-info'); });
        es.addEventListener('request', function (e) { linea(salidaId, 'request(' + dato(e).n + ') -> el suscriptor pide 1 elemento', 'rx-request'); });
        es.addEventListener('onNext', function (e) { linea(salidaId, 'onNext -> ' + describir(dato(e)), 'rx-onnext'); });
        es.addEventListener('cancel', function (e) { linea(salidaId, dato(e).mensaje, 'rx-cancel'); cerrar(); });
        es.addEventListener('onComplete', function (e) { linea(salidaId, 'onComplete -> ' + dato(e).mensaje, 'rx-oncomplete'); cerrar(); });
        es.addEventListener('onError', function (e) { linea(salidaId, 'onError -> ' + dato(e).mensaje, 'rx-onerror'); cerrar(); });
        es.onerror = function () {
            if (streams[salidaId] === es) {
                linea(salidaId, 'Conexión cerrada por el servidor.', 'rx-info');
                cerrar();
            }
        };
    }

    // Cancelar = cerrar la conexión: el servidor recibe el cancel() y deja de emitir
    function cancelar(salidaId, silencioso) {
        var es = streams[salidaId];
        if (!es) { return; }
        es.close();
        delete streams[salidaId];
        if (!silencioso) {
            linea(salidaId, 'cancel() -> conexión cerrada; el servidor cancela la suscripción.', 'rx-cancel');
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.getElementById('btn-flux').addEventListener('click', ejecutarFlux);
        document.getElementById('btn-total').addEventListener('click', contarActivas);
        document.getElementById('btn-uno').addEventListener('click', consultarUna);
        document.getElementById('btn-vacio').addEventListener('click', ejecutarVacio);

        document.querySelectorAll('[data-stream]').forEach(function (b) {
            b.addEventListener('click', function () { iniciarStream(b.dataset.stream, b.dataset.salida); });
        });
        document.querySelectorAll('[data-cancelar]').forEach(function (b) {
            b.addEventListener('click', function () { cancelar(b.dataset.cancelar, false); });
        });
        document.querySelectorAll('[data-limpiar]').forEach(function (b) {
            b.addEventListener('click', function () {
                cancelar(b.dataset.limpiar, true);
                limpiar(b.dataset.limpiar);
            });
        });
    });
})();
