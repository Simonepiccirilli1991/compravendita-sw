package it.compravendita.gestionale.acquisti;

/**
 * Criterio con cui i costi accessori di un acquisto (spedizione, commissioni, dogana)
 * vengono distribuiti sulle righe.
 */
public enum MetodoRipartizione {

	/** In proporzione al valore della riga (prezzo unitario x quantità). */
	PREZZO("In proporzione al prezzo"),

	/** In parti uguali per ogni pezzo acquistato. */
	QUANTITA("In parti uguali per pezzo");

	private final String etichetta;

	MetodoRipartizione(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
