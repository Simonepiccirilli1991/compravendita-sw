package it.compravendita.gestionale.magazzino;

import java.util.List;

/**
 * Stato in cui si trova la merce di una riga d'acquisto.
 */
public enum StatoOggetto {

	IN_ARRIVO("In arrivo", "text-bg-info"),
	IN_STOCK("In magazzino", "text-bg-primary"),
	IN_VENDITA("In vendita", "text-bg-success"),
	VENDUTO("Venduto", "text-bg-secondary"),
	RESO("Reso al fornitore", "text-bg-dark"),
	SCARICATO("Scaricato", "text-bg-danger");

	private final String etichetta;

	private final String classeBadge;

	StatoOggetto(String etichetta, String classeBadge) {
		this.etichetta = etichetta;
		this.classeBadge = classeBadge;
	}

	public String getEtichetta() {
		return etichetta;
	}

	public String getClasseBadge() {
		return classeBadge;
	}

	/** Stati che si possono scegliere quando si registra un acquisto. */
	public static List<StatoOggetto> iniziali() {
		return List.of(IN_ARRIVO, IN_STOCK, IN_VENDITA);
	}

	/**
	 * Stati in cui l'utente può spostare a mano un lotto. Vendita, reso e scarico
	 * hanno flussi dedicati.
	 */
	public List<StatoOggetto> transizioniManuali() {
		return switch (this) {
			case IN_ARRIVO -> List.of(IN_STOCK, IN_VENDITA);
			case IN_STOCK -> List.of(IN_VENDITA);
			case IN_VENDITA -> List.of(IN_STOCK);
			default -> List.of();
		};
	}

	/** Lotto con merce fisicamente disponibile o in arrivo. */
	public boolean isGiacenza() {
		return this == IN_ARRIVO || this == IN_STOCK || this == IN_VENDITA;
	}

}
