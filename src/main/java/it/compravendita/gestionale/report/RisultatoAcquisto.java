package it.compravendita.gestionale.report;

import java.math.BigDecimal;
import java.util.Map;

import it.compravendita.gestionale.common.Importi;

/**
 * Andamento economico di un acquisto (lotto o lotto misto): quanto è stato speso, quanto è
 * già rientrato dalle vendite e quanto resta in magazzino.
 *
 * @param investito totale dell'acquisto più i costi aggiunti dopo (es. grading)
 * @param ricavoNetto quota del netto delle vendite completate attribuita ai pezzi di questo acquisto
 * @param costoVenduto costo dei pezzi venduti
 * @param perditeRettifiche valore dei pezzi persi o danneggiati, meno quanto recuperato
 * @param valoreResiduo valore a costo dei pezzi ancora disponibili
 * @param righe risultato per riga d'acquisto (chiave = id della riga)
 */
public record RisultatoAcquisto(BigDecimal investito, int pezzi, int pezziVenduti, int pezziResidui,
		BigDecimal ricavoNetto, BigDecimal costoVenduto, BigDecimal perditeRettifiche, BigDecimal valoreResiduo,
		Map<Long, Riga> righe) {

	/** Profitto realizzato sui pezzi venduti. */
	public BigDecimal profittoRealizzato() {
		return ricavoNetto.subtract(costoVenduto);
	}

	/** Profitto realizzato meno le perdite da rettifica. */
	public BigDecimal risultato() {
		return profittoRealizzato().subtract(perditeRettifiche);
	}

	/** ROI % sui pezzi venduti. */
	public BigDecimal roi() {
		return Importi.percentuale(profittoRealizzato(), costoVenduto);
	}

	/** Percentuale dell'investimento già rientrata come netto incassato. */
	public BigDecimal recupero() {
		return Importi.percentuale(ricavoNetto, investito);
	}

	/** Investimento rientrato: il netto incassato copre la spesa. */
	public boolean isRipagato() {
		return investito.signum() > 0 && ricavoNetto.compareTo(investito) >= 0;
	}

	public record Riga(int pezziVenduti, BigDecimal ricavoNetto, BigDecimal costoVenduto) {

		public BigDecimal profitto() {
			return ricavoNetto.subtract(costoVenduto);
		}

		public BigDecimal roi() {
			return Importi.percentuale(profitto(), costoVenduto);
		}

	}

}
