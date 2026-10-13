package it.compravendita.gestionale.catalogo;

import java.util.List;

/**
 * Definizione di un attributo specifico di categoria, usata per costruire il form
 * e per validare/convertire i valori inseriti.
 */
public record CampoAttributo(String chiave, String etichetta, Tipo tipo, boolean obbligatorio,
		List<String> opzioni, Double minimo, Double massimo) {

	public enum Tipo {

		TESTO, INTERO, DECIMALE, SI_NO, SCELTA

	}

	public static CampoAttributo testo(String chiave, String etichetta) {
		return new CampoAttributo(chiave, etichetta, Tipo.TESTO, false, List.of(), null, null);
	}

	public static CampoAttributo intero(String chiave, String etichetta, double minimo, double massimo) {
		return new CampoAttributo(chiave, etichetta, Tipo.INTERO, false, List.of(), minimo, massimo);
	}

	public static CampoAttributo decimale(String chiave, String etichetta, double minimo, double massimo) {
		return new CampoAttributo(chiave, etichetta, Tipo.DECIMALE, false, List.of(), minimo, massimo);
	}

	public static CampoAttributo siNo(String chiave, String etichetta) {
		return new CampoAttributo(chiave, etichetta, Tipo.SI_NO, false, List.of(), null, null);
	}

	public static CampoAttributo scelta(String chiave, String etichetta, String... opzioni) {
		return new CampoAttributo(chiave, etichetta, Tipo.SCELTA, false, List.of(opzioni), null, null);
	}

	public CampoAttributo richiesto() {
		return new CampoAttributo(chiave, etichetta, tipo, true, opzioni, minimo, massimo);
	}

}
