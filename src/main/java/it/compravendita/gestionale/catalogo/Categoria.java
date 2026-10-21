package it.compravendita.gestionale.catalogo;

public enum Categoria {

	TAZZA("Tazza"),
	TAZZA_TERMICA("Tazza termica / Borraccia"),
	BICCHIERE("Bicchiere"),
	SET_REGALO("Set / Confezione regalo"),
	ACCESSORIO("Accessori"),
	ALTRO("Altro");

	private final String etichetta;

	Categoria(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
