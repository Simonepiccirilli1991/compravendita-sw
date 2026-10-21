package it.compravendita.gestionale.catalogo;

/**
 * Condizione dell'oggetto, sulla scala usata dai marketplace generalisti (Vinted, eBay, Subito).
 */
public enum Condizione {

	NUOVO_CONFEZIONATO("Nuovo con confezione"),
	NUOVO("Nuovo senza confezione"),
	COME_NUOVO("Come nuovo"),
	OTTIMO("Ottimo"),
	BUONO("Buono"),
	CON_DIFETTI("Con difetti");

	private final String etichetta;

	Condizione(String etichetta) {
		this.etichetta = etichetta;
	}

	public String getEtichetta() {
		return etichetta;
	}

}
