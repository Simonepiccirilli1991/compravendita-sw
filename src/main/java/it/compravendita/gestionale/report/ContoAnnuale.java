package it.compravendita.gestionale.report;

import java.math.BigDecimal;

/**
 * Riepilogo di un anno solare, utile per la dichiarazione: acquisti, vendite, costi e
 * rimanenze di magazzino a fine anno (valutate al costo).
 *
 * @param acquistato merce e costi accessori degli acquisti dell'anno
 * @param costiGrading costi di grading dei rientri dell'anno (già inclusi nel costo delle carte)
 * @param spedizioniIncassate spedizioni pagate dagli acquirenti nelle vendite completate
 * @param commissioni commissioni di piattaforma e pagamento delle vendite completate
 * @param costiVendita spedizioni, imballaggi e altri costi delle vendite completate
 * @param rimanenzeFinali valore al costo dei pezzi in giacenza al 31/12
 */
public record ContoAnnuale(int anno, BigDecimal acquistato, BigDecimal costiGrading, Risultato vendite,
		BigDecimal spedizioniIncassate, BigDecimal commissioni, BigDecimal costiVendita, BigDecimal perditeRettifiche,
		BigDecimal speseGenerali, BigDecimal rimanenzeFinali) {

	/** Profitto delle vendite − perdite da rettifica − spese generali. */
	public BigDecimal profittoNetto() {
		return vendite.profitto().subtract(perditeRettifiche).subtract(speseGenerali);
	}

}
