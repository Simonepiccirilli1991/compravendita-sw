package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;

/**
 * Valori usati dal {@link CalcoloPrezzi}: quelli salvati ({@link ParametriPrezzo}) o quelli di un form
 * non ancora salvato. Le percentuali sono espresse in centesimi (40 = 40%).
 */
public interface ValoriPrezzo {

	BigDecimal getCoefficienteRedditivita();

	BigDecimal getAliquotaImposta();

	BigDecimal getAliquotaContributi();

	/** Riduzione dei contributi INPS del regime agevolato dei forfettari (35), 0 se non richiesta. */
	BigDecimal getRiduzioneContributi();

	BigDecimal getMinimaleInps();

	/** Contributo annuo di maternità, dovuto per intero anche nel regime agevolato. */
	BigDecimal getContributoMaternita();

	/** Costi fissi annui oltre ai contributi INPS (commercialista, abbonamenti, conto, ...). */
	BigDecimal getAltriCostiFissi();

	BigDecimal getRicaviAnnuiPrevisti();

	BigDecimal getMargineDesiderato();

	BigDecimal getCostoImballaggio();

	BigDecimal getCostoSpedizione();

}
