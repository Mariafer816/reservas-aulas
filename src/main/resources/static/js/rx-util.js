// Funciones compartidas por las páginas de demostración reactiva (/reactivo y /webflux).
window.RxUtil = (function () {
    'use strict';

    function hora() {
        return new Date().toLocaleTimeString('es-CO', { hour12: false });
    }

    // Agrega una línea con la hora a la lista de salida. Usa textContent: nunca se interpreta HTML.
    function linea(salidaId, texto, clase) {
        var salida = document.getElementById(salidaId);
        var li = document.createElement('li');
        var h = document.createElement('span');
        h.className = 'hora';
        h.textContent = hora();
        var t = document.createElement('span');
        t.className = clase || 'rx-info';
        t.textContent = texto;
        li.appendChild(h);
        li.appendChild(t);
        salida.appendChild(li);
        salida.scrollTop = salida.scrollHeight;
    }

    function limpiar(salidaId) {
        document.getElementById(salidaId).innerHTML = '';
    }

    // GET que devuelve { estado, cuerpo } sin lanzar error por códigos 4xx/5xx
    function pedir(url) {
        return fetch(url, { headers: { 'Accept': 'application/json' } }).then(function (resp) {
            return resp.json().catch(function () { return null; }).then(function (cuerpo) {
                return { estado: resp.status, cuerpo: cuerpo };
            });
        });
    }

    return { hora: hora, linea: linea, limpiar: limpiar, pedir: pedir };
})();
