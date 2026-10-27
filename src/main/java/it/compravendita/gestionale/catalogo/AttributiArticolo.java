package it.compravendita.gestionale.catalogo;

/**
 * Chiavi standard usate in {@link Articolo#getAttributi()} per ogni categoria.
 */
public final class AttributiArticolo {

	// Carte (singole, gradate) e sealed TCG
	public static final String SET = "set";
	public static final String NUMERO_CARTA = "numeroCarta";
	public static final String RARITA = "rarita";
	public static final String EDIZIONE = "edizione";

	// Carte gradate
	public static final String ENTE_GRADING = "enteGrading";
	public static final String VOTO = "voto";
	public static final String NUMERO_CERTIFICATO = "numeroCertificato";

	// Sealed TCG (booster box, ETB, display, ...)
	public static final String TIPO_PRODOTTO = "tipoProdotto";

	// Videogiochi
	public static final String CONSOLE = "console";
	public static final String REGIONE = "regione";
	public static final String COMPLETEZZA = "completezza";
	public static final String RETRO = "retro";

	// LEGO
	public static final String NUMERO_SET = "numeroSet";
	public static final String TEMA = "tema";
	public static final String ANNO = "anno";
	public static final String STATO_SCATOLA = "statoScatola";

	// Tazze, bicchieri e oggettistica fuori dal core (stesse chiavi del branch tazze)
	public static final String MATERIALE = "materiale";
	public static final String CAPACITA = "capacita";
	public static final String COLORE = "colore";
	public static final String SOGGETTO = "soggetto";
	public static final String TIPO_OGGETTO = "tipoOggetto";

	private AttributiArticolo() {
	}

}
