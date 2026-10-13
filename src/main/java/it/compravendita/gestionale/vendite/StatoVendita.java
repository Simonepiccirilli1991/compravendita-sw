package it.compravendita.gestionale.vendite;

public enum StatoVendita {

	COMPLETATA("Completata"),
	ANNULLATA("Annullata"),
	RESA("Resa dal cliente");

	private final String etichetta;

	StatoVendita(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
