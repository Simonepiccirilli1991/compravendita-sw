package it.compravendita.gestionale.report;

import java.math.BigDecimal;

import it.compravendita.gestionale.common.Importi;

/**
 * Conto economico di un periodo: risultato delle vendite meno le perdite di magazzino e le
 * spese generali (quelle non attribuite a un lotto).
 */
public record RiepilogoPeriodo(Risultato vendite, BigDecimal perditeRettifiche, BigDecimal speseGenerali,
		BigDecimal acquistato) {

	/** Profitto delle vendite − perdite da rettifica − spese generali. */
	public BigDecimal profittoNetto() {
		return vendite.profitto().subtract(perditeRettifiche).subtract(speseGenerali);
	}

	/** ROI % complessivo: profitto netto / costo del venduto. */
	public BigDecimal roiNetto() {
		return Importi.percentuale(profittoNetto(), vendite.costoVenduto());
	}

}
