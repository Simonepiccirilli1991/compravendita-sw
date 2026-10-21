package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.piattaforme.Piattaforma;

/**
 * Calcolo dei prezzi di vendita suggeriti per un commerciante in regime forfettario.
 * <p>
 * Il reddito è una parte fissa dei ricavi (il coefficiente di redditività). Sul reddito si pagano:
 * <ul>
 * <li>i contributi INPS: sul minimale sono dovuti comunque, anche con reddito più basso; sulla parte
 * che supera il minimale si paga l'aliquota. Nel regime agevolato entrambi sono ridotti del 35%,
 * mentre il contributo di maternità resta intero (circolari INPS n. 35/2016 e n. 14/2026);</li>
 * <li>l'imposta sostitutiva, sul reddito meno i contributi versati.</li>
 * </ul>
 * Con i ricavi previsti in un anno si calcola quanto costa ogni euro venduto in più (il carico
 * marginale, applicato al prezzo) e la parte fissa che resta (contributi sul minimale e altri costi
 * fissi), divisa sui ricavi come percentuale di ogni prezzo. Così, vendendo tutto al prezzo minimo con i
 * ricavi previsti, a fine anno si va esattamente in pari:
 * <ul>
 * <li><b>Prezzo minimo</b>: copre costo d'acquisto, commissioni, imballaggio, spedizione, imposta e
 * contributi, compresa la quota dei costi fissi.</li>
 * <li><b>Prezzo consigliato</b>: resta in più il margine desiderato sul costo d'acquisto; arrotondato
 * per eccesso ai 50 centesimi.</li>
 * </ul>
 */
public final class CalcoloPrezzi {

	private static final MathContext PRECISIONE = MathContext.DECIMAL64;

	private static final BigDecimal CENTO = BigDecimal.valueOf(100);

	private static final BigDecimal MEZZO_EURO = new BigDecimal("0.50");

	private final BigDecimal coefficiente;

	private final BigDecimal imposta;

	private final BigDecimal aliquotaContributiRidotta;

	private final BigDecimal ricaviPrevisti;

	private final BigDecimal margine;

	private final BigDecimal costoImballaggio;

	private final BigDecimal costoSpedizione;

	private final String piattaforma;

	private final BigDecimal commissionePercentuale;

	private final BigDecimal commissioneFissa;

	private final ConteggioAnnuo anno;

	private final BigDecimal caricoMarginale;

	private final BigDecimal quotaFissi;

	private CalcoloPrezzi(ValoriPrezzo v, Piattaforma p) {
		coefficiente = frazione(v.getCoefficienteRedditivita());
		imposta = frazione(v.getAliquotaImposta());
		aliquotaContributiRidotta = frazione(v.getAliquotaContributi())
			.multiply(BigDecimal.ONE.subtract(frazione(v.getRiduzioneContributi())), PRECISIONE);
		ricaviPrevisti = Importi.nonNullo(v.getRicaviAnnuiPrevisti());
		margine = frazione(v.getMargineDesiderato());
		costoImballaggio = Importi.nonNullo(v.getCostoImballaggio());
		costoSpedizione = Importi.nonNullo(v.getCostoSpedizione());
		if (p != null) {
			piattaforma = p.getNome();
			commissionePercentuale = Importi.nonNullo(p.getFeePercentuale())
				.add(Importi.nonNullo(p.getFeePagamentoPercentuale()));
			commissioneFissa = Importi.nonNullo(p.getFeeFissa()).add(Importi.nonNullo(p.getFeePagamentoFissa()));
		}
		else {
			piattaforma = null;
			commissionePercentuale = BigDecimal.ZERO;
			commissioneFissa = BigDecimal.ZERO;
		}

		BigDecimal minimale = Importi.nonNullo(v.getMinimaleInps());
		BigDecimal reddito = Importi.importo(ricaviPrevisti.multiply(coefficiente));
		boolean sopraMinimale = reddito.compareTo(minimale) > 0;
		BigDecimal contributiMinimale = Importi.importo(minimale.multiply(aliquotaContributiRidotta))
			.add(Importi.importo(v.getContributoMaternita()));
		BigDecimal contributiEccedenza = sopraMinimale
				? Importi.importo(reddito.subtract(minimale).multiply(aliquotaContributiRidotta)) : Importi.importo(null);
		BigDecimal contributi = contributiMinimale.add(contributiEccedenza);
		BigDecimal impostaAnnua = Importi.importo(reddito.subtract(contributi).max(BigDecimal.ZERO).multiply(imposta));

		// ogni euro venduto in più: contributi solo sopra il minimale, imposta sul reddito meno i contributi
		BigDecimal contributiMarginali = sopraMinimale ? aliquotaContributiRidotta : BigDecimal.ZERO;
		caricoMarginale = coefficiente.multiply(
				contributiMarginali.add(imposta.multiply(BigDecimal.ONE.subtract(contributiMarginali))), PRECISIONE);
		// quello che il carico marginale non copre è un costo fisso (contributi sul minimale)
		BigDecimal tasseFisse = Importi.importo(contributi.add(impostaAnnua)
			.subtract(ricaviPrevisti.multiply(caricoMarginale))
			.max(BigDecimal.ZERO));
		BigDecimal altri = Importi.importo(v.getAltriCostiFissi());
		BigDecimal costiFissi = tasseFisse.add(altri);
		quotaFissi = ricaviPrevisti.signum() > 0 ? costiFissi.divide(ricaviPrevisti, PRECISIONE) : BigDecimal.ZERO;
		anno = new ConteggioAnnuo(Importi.importo(ricaviPrevisti), reddito, minimale, sopraMinimale, contributiMinimale,
				contributiEccedenza, contributi, impostaAnnua, contributi.add(impostaAnnua), tasseFisse, altri, costiFissi,
				percentuale(aliquotaContributiRidotta), percentuale(caricoMarginale), percentuale(quotaFissi));
	}

	public static CalcoloPrezzi di(ValoriPrezzo valori, Piattaforma piattaforma) {
		return new CalcoloPrezzi(valori, piattaforma);
	}

	public static CalcoloPrezzi da(ParametriPrezzo parametri) {
		return new CalcoloPrezzi(parametri, parametri.getPiattaforma());
	}

	/** Conteggio di contributi e imposta dell'anno con i ricavi previsti. */
	public ConteggioAnnuo anno() {
		return anno;
	}

	public String piattaforma() {
		return piattaforma;
	}

	public BigDecimal commissionePercentuale() {
		return commissionePercentuale;
	}

	public BigDecimal commissioneFissa() {
		return commissioneFissa;
	}

	/** Margine desiderato in percentuale. */
	public BigDecimal marginePercentuale() {
		return percentuale(margine);
	}

	/** Imposta e contributi di ogni euro venduto in più, in percentuale del prezzo. */
	public BigDecimal caricoMarginalePercentuale() {
		return anno.caricoMarginalePercentuale();
	}

	/** Costi di ogni vendita che non dipendono dal prezzo: imballaggio, spedizione, commissioni fisse. */
	public BigDecimal costiPerPezzo() {
		return costoImballaggio.add(costoSpedizione).add(commissioneFissa);
	}

	/** Parte del prezzo che resta dopo commissioni percentuali, imposta, contributi e costi fissi. */
	private BigDecimal quotaCheResta() {
		return BigDecimal.ONE.subtract(frazione(commissionePercentuale)).subtract(caricoMarginale).subtract(quotaFissi);
	}

	/** Vero se commissioni, tasse e costi fissi restano sotto il 100% del prezzo, cioè se i prezzi sono calcolabili. */
	public boolean isCalcolabile() {
		return quotaCheResta().signum() > 0;
	}

	/** Prezzi suggeriti per un pezzo con il costo unitario indicato; null se non calcolabili. */
	public PrezziSuggeriti calcola(BigDecimal costoUnitario) {
		if (costoUnitario == null || !isCalcolabile()) {
			return null;
		}
		BigDecimal resta = quotaCheResta();
		BigDecimal minimo = costoUnitario.add(costiPerPezzo())
			.divide(resta, PRECISIONE)
			.setScale(Importi.SCALA_IMPORTO, RoundingMode.CEILING);
		BigDecimal consigliato = costoUnitario.multiply(BigDecimal.ONE.add(margine))
			.add(costiPerPezzo())
			.divide(resta, PRECISIONE)
			.divide(MEZZO_EURO, 0, RoundingMode.CEILING)
			.multiply(MEZZO_EURO)
			.setScale(Importi.SCALA_IMPORTO);
		if (consigliato.compareTo(minimo) < 0) {
			consigliato = minimo;
		}
		return new PrezziSuggeriti(Importi.importo(costoUnitario), minimo, consigliato,
				ripartizione(minimo, costoUnitario), ripartizione(consigliato, costoUnitario));
	}

	/** Dove va a finire un prezzo di vendita: commissioni, spese, imposta e contributi, costi fissi, guadagno. */
	public Ripartizione ripartizione(BigDecimal prezzo, BigDecimal costoUnitario) {
		BigDecimal commissioni = Importi.importo(prezzo.multiply(frazione(commissionePercentuale)).add(commissioneFissa));
		BigDecimal spese = Importi.importo(costoImballaggio.add(costoSpedizione));
		BigDecimal tasse = Importi.importo(prezzo.multiply(caricoMarginale));
		BigDecimal fissi = Importi.importo(prezzo.multiply(quotaFissi));
		BigDecimal guadagno = prezzo.subtract(costoUnitario).subtract(commissioni).subtract(spese).subtract(tasse)
			.subtract(fissi);
		return new Ripartizione(Importi.importo(prezzo), commissioni, spese, tasse, fissi, Importi.importo(guadagno));
	}

	private static BigDecimal frazione(BigDecimal percentuale) {
		return Importi.nonNullo(percentuale).divide(CENTO, PRECISIONE);
	}

	private static BigDecimal percentuale(BigDecimal frazione) {
		return frazione.multiply(CENTO).setScale(2, Importi.ARROTONDAMENTO);
	}

	/**
	 * Prezzi suggeriti per un pezzo.
	 *
	 * @param alMinimo ripartizione del prezzo minimo
	 * @param alConsigliato ripartizione del prezzo consigliato
	 */
	public record PrezziSuggeriti(BigDecimal costo, BigDecimal minimo, BigDecimal consigliato, Ripartizione alMinimo,
			Ripartizione alConsigliato) {
	}

	/**
	 * Ripartizione di un prezzo di vendita.
	 *
	 * @param tasse imposta e contributi dovuti per questa vendita
	 * @param costiFissi quota della vendita che copre i costi fissi (contributi sul minimale e altri)
	 * @param guadagno quello che resta dopo costo d'acquisto e tutti gli altri costi
	 */
	public record Ripartizione(BigDecimal prezzo, BigDecimal commissioni, BigDecimal spese, BigDecimal tasse,
			BigDecimal costiFissi, BigDecimal guadagno) {
	}

	/**
	 * Contributi e imposta dell'anno con i ricavi previsti.
	 *
	 * @param tasseFisse parte di contributi e imposta non coperta dal carico marginale delle vendite
	 * @param costiFissi tasse fisse più gli altri costi fissi, da coprire con i prezzi
	 * @param quotaFissiPercentuale costi fissi in percentuale dei ricavi previsti
	 */
	public record ConteggioAnnuo(BigDecimal ricavi, BigDecimal reddito, BigDecimal minimale, boolean sopraMinimale,
			BigDecimal contributiMinimale, BigDecimal contributiEccedenza, BigDecimal contributi, BigDecimal imposta,
			BigDecimal tasse, BigDecimal tasseFisse, BigDecimal altriCostiFissi, BigDecimal costiFissi,
			BigDecimal aliquotaContributiPercentuale, BigDecimal caricoMarginalePercentuale,
			BigDecimal quotaFissiPercentuale) {
	}

}
