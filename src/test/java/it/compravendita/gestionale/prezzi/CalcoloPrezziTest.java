package it.compravendita.gestionale.prezzi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.prezzi.CalcoloPrezzi.ConteggioAnnuo;
import it.compravendita.gestionale.prezzi.CalcoloPrezzi.PrezziSuggeriti;

class CalcoloPrezziTest {

	/**
	 * Commerciante forfettario agevolato 2026 (circolare INPS n. 14/2026): coefficiente 40%, imposta 5%,
	 * INPS 24,48% ridotto del 35%, minimale 18.808 €, maternità 7,44 €; imballaggio 1 €, margine 30%.
	 */
	private static ParametriPrezzoForm valori(String ricavi) {
		ParametriPrezzoForm v = new ParametriPrezzoForm();
		v.setCoefficienteRedditivita(new BigDecimal("40"));
		v.setAliquotaImposta(new BigDecimal("5"));
		v.setAliquotaContributi(new BigDecimal("24.48"));
		v.setRiduzioneContributi(new BigDecimal("35"));
		v.setMinimaleInps(new BigDecimal("18808"));
		v.setContributoMaternita(new BigDecimal("7.44"));
		v.setAltriCostiFissi(BigDecimal.ZERO);
		v.setRicaviAnnuiPrevisti(new BigDecimal(ricavi));
		v.setMargineDesiderato(new BigDecimal("30"));
		v.setCostoImballaggio(BigDecimal.ONE);
		v.setCostoSpedizione(BigDecimal.ZERO);
		return v;
	}

	@Test
	void contributiSulMinimaleDelRegimeAgevolato() {
		ConteggioAnnuo anno = CalcoloPrezzi.di(valori("10000"), null).anno();

		// 18.808 × 24,48% × 65% = 2.992,73 + 7,44 di maternità intera
		assertThat(anno.contributiMinimale()).isEqualByComparingTo("3000.17");
		assertThat(anno.aliquotaContributiPercentuale()).isEqualByComparingTo("15.91");
		assertThat(anno.reddito()).isEqualByComparingTo("4000.00");
		assertThat(anno.sopraMinimale()).isFalse();
		assertThat(anno.contributiEccedenza()).isEqualByComparingTo("0");
		// (4.000 − 3.000,17) × 5%
		assertThat(anno.imposta()).isEqualByComparingTo("49.99");
		assertThat(anno.tasse()).isEqualByComparingTo("3050.16");
	}

	@Test
	void sottoIlMinimaleOgniVenditaPagaSoloLImposta() {
		CalcoloPrezzi c = CalcoloPrezzi.di(valori("10000"), null);

		// 40% × 5%: i contributi sono già quelli fissi sul minimale
		assertThat(c.caricoMarginalePercentuale()).isEqualByComparingTo("2.00");
		// 3.050,16 − 2% di 10.000 = 2.850,16 di costi fissi, il 28,5% di ogni prezzo
		assertThat(c.anno().costiFissi()).isEqualByComparingTo("2850.16");
		assertThat(c.anno().quotaFissiPercentuale()).isEqualByComparingTo("28.50");

		PrezziSuggeriti p = c.calcola(BigDecimal.TEN);
		// (10 + 1) / (1 − 0,02 − 0,285016) = 15,8277
		assertThat(p.minimo()).isEqualByComparingTo("15.83");
		// (13 + 1) / 0,694984 = 20,14 → per eccesso ai 50 centesimi
		assertThat(p.consigliato()).isEqualByComparingTo("20.50");
	}

	@Test
	void sopraIlMinimaleSiPaganoAncheIContributiSullaVendita() {
		CalcoloPrezzi c = CalcoloPrezzi.di(valori("60000"), null);
		ConteggioAnnuo anno = c.anno();

		assertThat(anno.sopraMinimale()).isTrue();
		// (24.000 − 18.808) × 15,912%
		assertThat(anno.contributiEccedenza()).isEqualByComparingTo("826.15");
		assertThat(anno.imposta()).isEqualByComparingTo("1008.68");
		// 40% × (15,912% + 5% × 84,088%)
		assertThat(c.caricoMarginalePercentuale()).isEqualByComparingTo("8.05");
		assertThat(c.calcola(BigDecimal.TEN).minimo()).isEqualByComparingTo("11.97");
	}

	@Test
	void conGliIncassiPrevistiLeTasseDellAnnoSonoCoperte() {
		for (String ricavi : new String[] { "3000", "10000", "30000", "47020", "60000", "85000" }) {
			CalcoloPrezzi c = CalcoloPrezzi.di(valori(ricavi), null);
			// tutte le vendite dell'anno insieme: tasse sulle vendite più quota dei costi fissi
			CalcoloPrezzi.Ripartizione tutte = c.ripartizione(new BigDecimal(ricavi), BigDecimal.ZERO);
			assertThat(tutte.tasse().add(tutte.costiFissi())).as("incassi %s", ricavi)
				.isCloseTo(c.anno().tasse(), Offset.offset(new BigDecimal("0.02")));
		}
	}

	@Test
	void alPrezzoMinimoSiVaInPari() {
		CalcoloPrezzi c = CalcoloPrezzi.di(valori("10000"), null);
		for (String costo : new String[] { "0.50", "3", "7.35", "12.9999", "48", "199.99" }) {
			PrezziSuggeriti p = c.calcola(new BigDecimal(costo));
			assertThat(p.alMinimo().guadagno()).as("costo %s", costo).isBetween(new BigDecimal("-0.02"), new BigDecimal("0.02"));
			assertThat(c.ripartizione(p.minimo().subtract(new BigDecimal("0.05")), new BigDecimal(costo)).guadagno())
				.as("sotto il minimo, costo %s", costo)
				.isNegative();
			// al consigliato resta almeno il margine del 30% sul costo
			assertThat(p.alConsigliato().guadagno()).as("consigliato, costo %s", costo)
				.isGreaterThanOrEqualTo(new BigDecimal(costo).multiply(new BigDecimal("0.30")).subtract(new BigDecimal("0.02")));
		}
	}

	@Test
	void senzaRegimeAgevolatoIContributiSonoPieni() {
		ParametriPrezzoForm v = valori("10000");
		v.setRiduzioneContributi(BigDecimal.ZERO);

		// 18.808 × 24,48% = 4.604,20 + 7,44: gli importi della circolare INPS n. 14/2026
		assertThat(CalcoloPrezzi.di(v, null).anno().contributiMinimale()).isEqualByComparingTo("4611.64");
	}

	@Test
	void commissioniDellaPiattaformaEntranoNelPrezzo() {
		Piattaforma ebay = new Piattaforma("eBay");
		ebay.setFeePercentuale(new BigDecimal("8"));
		ebay.setFeeFissa(new BigDecimal("0.35"));
		ebay.setFeePagamentoPercentuale(new BigDecimal("2"));
		ebay.setFeePagamentoFissa(new BigDecimal("0.15"));
		CalcoloPrezzi c = CalcoloPrezzi.di(valori("10000"), ebay);

		PrezziSuggeriti p = c.calcola(BigDecimal.TEN);

		// (10 + 1 + 0,50) / (1 − 0,10 − 0,02 − 0,285016) = 19,3271
		assertThat(p.minimo()).isEqualByComparingTo("19.33");
		assertThat(p.alMinimo().commissioni()).isEqualByComparingTo("2.43");
		assertThat(c.piattaforma()).isEqualTo("eBay");
	}

	@Test
	void incassiTroppoBassiNonPermettonoDiAndareInPari() {
		// 3.000 € di contributi fissi su 2.000 € di incassi: impossibile
		CalcoloPrezzi c = CalcoloPrezzi.di(valori("2000"), null);

		assertThat(c.isCalcolabile()).isFalse();
		assertThat(c.calcola(BigDecimal.TEN)).isNull();
		assertThat(CalcoloPrezzi.di(valori("10000"), null).calcola(null)).isNull();
	}

}
