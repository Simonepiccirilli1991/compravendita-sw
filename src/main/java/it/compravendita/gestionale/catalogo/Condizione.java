package it.compravendita.gestionale.catalogo;

/**
 * Condizione dell'oggetto, sulla scala usata dai principali marketplace di carte.
 */
public enum Condizione {

	SIGILLATO("Sigillato"),
	MINT("Mint"),
	NEAR_MINT("Near Mint"),
	EXCELLENT("Excellent"),
	GOOD("Good"),
	LIGHT_PLAYED("Light Played"),
	PLAYED("Played"),
	POOR("Poor");

	private final String etichetta;

	Condizione(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
