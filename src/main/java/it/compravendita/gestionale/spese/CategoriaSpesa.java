package it.compravendita.gestionale.spese;

public enum CategoriaSpesa {

	MATERIALI("Materiali e imballaggi"),
	SPEDIZIONE("Spedizione"),
	PUBBLICITA("Pubblicità / promozioni"),
	ABBONAMENTO("Abbonamento"),
	FIERA("Fiera / evento"),
	ALTRO("Altro");

	private final String etichetta;

	CategoriaSpesa(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
