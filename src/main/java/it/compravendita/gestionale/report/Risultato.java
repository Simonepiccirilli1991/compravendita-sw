package it.compravendita.gestionale.report;

import java.math.BigDecimal;

import it.compravendita.gestionale.common.Importi;

/**
 * Risultato economico aggregato delle vendite (per un periodo o per una voce: categoria,
 * brand, piattaforma, articolo, mese).
 *
 * @param chiave identificativo della voce (id, codice categoria, "AAAA-MM"), null per il totale
 * @param vendite numero di vendite completate
 * @param pezzi pezzi venduti nelle vendite completate
 * @param lordo prezzo della merce venduta
 * @param netto quanto resta dopo commissioni e costi di spedizione/imballaggio
 * @param costoVenduto costo d'acquisto della merce venduta (FIFO, costi accessori inclusi)
 * @param costiResi costi rimasti a carico per i resi
 */
public record Risultato(String chiave, String etichetta, long vendite, long pezzi, BigDecimal lordo, BigDecimal netto,
		BigDecimal costoVenduto, BigDecimal costiResi) {

	public static Risultato vuoto(String chiave, String etichetta) {
		BigDecimal zero = Importi.importo(BigDecimal.ZERO);
		return new Risultato(chiave, etichetta, 0, 0, zero, zero, zero, zero);
	}

	/** Profitto = netto − costo del venduto − costi dei resi. */
	public BigDecimal profitto() {
		return netto.subtract(costoVenduto).subtract(costiResi);
	}

	/** ROI % = profitto / costo del venduto × 100; null se il costo è zero. */
	public BigDecimal roi() {
		return Importi.percentuale(profitto(), costoVenduto);
	}

	/** Margine % = profitto / lordo × 100; null se il lordo è zero. */
	public BigDecimal margine() {
		return Importi.percentuale(profitto(), lordo);
	}

	/** Prezzo medio di vendita per pezzo. */
	public BigDecimal prezzoMedio() {
		return pezzi == 0 ? null
				: lordo.divide(BigDecimal.valueOf(pezzi), Importi.SCALA_IMPORTO, Importi.ARROTONDAMENTO);
	}

	/** Somma due risultati (stessa chiave del primo). */
	public Risultato piu(Risultato altro) {
		return new Risultato(chiave, etichetta, vendite + altro.vendite, pezzi + altro.pezzi, lordo.add(altro.lordo),
				netto.add(altro.netto), costoVenduto.add(altro.costoVenduto), costiResi.add(altro.costiResi));
	}

}
