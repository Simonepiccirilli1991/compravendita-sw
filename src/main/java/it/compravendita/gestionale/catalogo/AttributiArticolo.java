package it.compravendita.gestionale.catalogo;

/**
 * Chiavi standard usate in {@link Articolo#getAttributi()} per ogni categoria.
 */
public final class AttributiArticolo {

	// Tazze, tazze termiche, bicchieri, accessori
	public static final String MATERIALE = "materiale";
	public static final String CAPACITA = "capacita";
	public static final String COLORE = "colore";
	public static final String SOGGETTO = "soggetto";
	public static final String FORMA = "forma";

	// Set e confezioni regalo
	public static final String NUMERO_PEZZI = "numeroPezzi";
	public static final String CONTENUTO = "contenuto";

	// Accessori (sottobicchieri, cucchiaini, ...)
	public static final String TIPO_ACCESSORIO = "tipoAccessorio";

	private AttributiArticolo() {
	}

}
