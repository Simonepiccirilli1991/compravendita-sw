package it.compravendita.gestionale.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Convenzioni di arrotondamento per gli importi in euro.
 */
public final class Importi {

	/** Scala degli importi monetari (centesimi). */
	public static final int SCALA_IMPORTO = 2;

	/** Scala dei costi unitari calcolati (ripartizioni, medie). */
	public static final int SCALA_COSTO_UNITARIO = 4;

	public static final RoundingMode ARROTONDAMENTO = RoundingMode.HALF_UP;

	private static final BigDecimal CENTO = BigDecimal.valueOf(100);

	private Importi() {
	}

	public static BigDecimal importo(BigDecimal valore) {
		return nonNullo(valore).setScale(SCALA_IMPORTO, ARROTONDAMENTO);
	}

	/**
	 * Rapporto in percentuale con un decimale (es. ROI = profitto / costo x 100); null se la base
	 * è zero o mancante, perché il rapporto non è definito.
	 */
	public static BigDecimal percentuale(BigDecimal valore, BigDecimal base) {
		if (valore == null || base == null || base.signum() == 0) {
			return null;
		}
		return valore.multiply(CENTO).divide(base, 1, ARROTONDAMENTO);
	}

	public static BigDecimal nonNullo(BigDecimal valore) {
		return valore != null ? valore : BigDecimal.ZERO;
	}

}
