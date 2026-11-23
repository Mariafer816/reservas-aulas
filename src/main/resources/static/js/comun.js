// Comportamientos comunes de las páginas. Reemplaza los atributos onsubmit/onclick incrustados en el HTML,
// para poder aplicar una Content-Security-Policy que no permita scripts en línea.
(function () {
    'use strict';

    // <form data-confirmar="¿Está seguro...?">: pide confirmación antes de enviar
    document.addEventListener('submit', function (e) {
        var mensaje = e.target && e.target.dataset ? e.target.dataset.confirmar : null;
        if (mensaje && !window.confirm(mensaje)) {
            e.preventDefault();
        }
    });

    // <button data-volver>: vuelve a la página anterior
    document.addEventListener('click', function (e) {
        var boton = e.target && e.target.closest ? e.target.closest('[data-volver]') : null;
        if (boton) {
            window.history.back();
        }
    });
})();
