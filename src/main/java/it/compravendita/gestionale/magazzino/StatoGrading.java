package it.compravendita.gestionale.magazzino;

public enum StatoGrading {

	INVIATO("Inviato", "text-bg-warning"),
	RIENTRATO("Rientrato", "text-bg-success"),
	ANNULLATO("Annullato", "text-bg-secondary");

	private final String etichetta;

	private final String classeBadge;

	StatoGrading(String etichetta, String classeBadge) {
		this.etichetta = etichetta;
		this.classeBadge = classeBadge;
	}

	public String getEtichetta() {
		return etichetta;
	}

	public String getClasseBadge() {
		return classeBadge;
	}

}
