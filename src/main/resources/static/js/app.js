// Errori delle richieste HTMX: htmx non sostituisce il contenuto per le risposte 4xx/5xx,
// quindi il messaggio (frammento "messaggio" di error.html) viene mostrato in un avviso.
(function () {
    function mostraErrore(testo) {
        var contenitore = document.getElementById('avvisi-errore');
        if (!contenitore) {
            return;
        }
        var avviso = document.createElement('div');
        avviso.className = 'alert alert-danger alert-dismissible fade show shadow-sm';
        avviso.setAttribute('role', 'alert');
        var icona = document.createElement('i');
        icona.className = 'bi bi-exclamation-triangle me-1';
        var messaggio = document.createElement('span');
        messaggio.textContent = testo;
        var chiudi = document.createElement('button');
        chiudi.type = 'button';
        chiudi.className = 'btn-close';
        chiudi.setAttribute('data-bs-dismiss', 'alert');
        chiudi.setAttribute('aria-label', 'Chiudi');
        avviso.append(icona, messaggio, chiudi);
        contenitore.append(avviso);
        setTimeout(function () { avviso.remove(); }, 8000);
    }

    document.addEventListener('htmx:responseError', function (evento) {
        var xhr = evento.detail.xhr;
        var testo = '';
        if (xhr.responseText) {
            testo = new DOMParser().parseFromString(xhr.responseText, 'text/html').body.textContent.trim();
        }
        if (xhr.status === 403) {
            testo = 'Sessione scaduta o permessi insufficienti: ricarica la pagina.';
        }
        mostraErrore(testo || 'Si è verificato un errore (codice ' + xhr.status + ').');
    });

    document.addEventListener('htmx:sendError', function () {
        mostraErrore("Impossibile contattare l'applicazione: è ancora avviata?");
    });
})();
