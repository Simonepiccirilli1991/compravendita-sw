package it.compravendita.gestionale.common;

/**
 * Operazione rifiutata per una regola di business; il messaggio è mostrato all'utente.
 */
public class OperazioneNonConsentitaException extends RuntimeException {

	public OperazioneNonConsentitaException(String messaggio) {
		super(messaggio);
	}

}
