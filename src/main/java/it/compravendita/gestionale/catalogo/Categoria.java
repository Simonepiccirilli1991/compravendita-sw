package it.compravendita.gestionale.catalogo;

public enum Categoria {

	CARTA_SINGOLA("Carta singola"),
	CARTA_GRADATA("Carta gradata"),
	SEALED_TCG("Sealed TCG"),
	VIDEOGIOCO("Videogioco"),
	LEGO("LEGO"),
	ALTRO("Altro");

	private final String etichetta;

	Categoria(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
