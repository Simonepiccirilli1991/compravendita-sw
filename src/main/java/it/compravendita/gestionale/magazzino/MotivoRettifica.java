package it.compravendita.gestionale.magazzino;

/**
 * Motivo per cui dei pezzi escono dal magazzino senza essere venduti.
 */
public enum MotivoRettifica {

	PERSO("Perso / smarrito"),
	DANNEGGIATO("Danneggiato"),
	USO_PERSONALE("Uso personale"),
	RESO_FORNITORE("Reso al fornitore"),
	CORREZIONE("Correzione inventario");

	private final String etichetta;

	MotivoRettifica(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

	/** Stato del lotto quando la rettifica lo azzera. */
	public StatoOggetto statoSeEsaurito() {
		return this == RESO_FORNITORE ? StatoOggetto.RESO : StatoOggetto.SCARICATO;
	}

}
