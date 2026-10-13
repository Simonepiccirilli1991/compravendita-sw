package it.compravendita.gestionale.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import it.compravendita.gestionale.common.Importi;

class RisultatoTest {

	@Test
	void formuleDiProfittoRoiEMargine() {
		Risultato r = new Risultato("X", "Voce", 2, 4, new BigDecimal("100.00"), new BigDecimal("85.00"),
				new BigDecimal("50.00"), new BigDecimal("5.00"));

		assertThat(r.profitto()).isEqualByComparingTo("30");
		assertThat(r.roi()).isEqualByComparingTo("60.0");
		assertThat(r.margine()).isEqualByComparingTo("30.0");
		assertThat(r.prezzoMedio()).isEqualByComparingTo("25");
	}

	@Test
	void venditaInPerdita() {
		Risultato r = new Risultato("X", "Voce", 1, 1, new BigDecimal("10.00"), new BigDecimal("8.00"),
				new BigDecimal("12.00"), BigDecimal.ZERO);

		assertThat(r.profitto()).isEqualByComparingTo("-4");
		assertThat(r.roi()).isEqualByComparingTo("-33.3");
	}

	@Test
	void costoELordoZeroNonDannoPercentuali() {
		Risultato vuoto = Risultato.vuoto(null, "Totale");

		assertThat(vuoto.profitto()).isEqualByComparingTo("0");
		assertThat(vuoto.roi()).isNull();
		assertThat(vuoto.margine()).isNull();
		assertThat(vuoto.prezzoMedio()).isNull();
		assertThat(Importi.percentuale(BigDecimal.ONE, null)).isNull();
	}

	@Test
	void sommaDiRisultati() {
		Risultato a = new Risultato("A", "A", 1, 2, new BigDecimal("20"), new BigDecimal("18"), new BigDecimal("10"),
				BigDecimal.ZERO);
		Risultato b = new Risultato("B", "B", 1, 1, new BigDecimal("30"), new BigDecimal("25"), new BigDecimal("15"),
				new BigDecimal("3"));

		Risultato somma = a.piu(b);

		assertThat(somma.chiave()).isEqualTo("A");
		assertThat(somma.vendite()).isEqualTo(2);
		assertThat(somma.pezzi()).isEqualTo(3);
		assertThat(somma.profitto()).isEqualByComparingTo("15");
	}

	@Test
	void riepilogoPeriodo() {
		Risultato vendite = new Risultato(null, "Totale", 3, 3, new BigDecimal("300"), new BigDecimal("250"),
				new BigDecimal("150"), BigDecimal.ZERO);
		RiepilogoPeriodo riepilogo = new RiepilogoPeriodo(vendite, new BigDecimal("20"), new BigDecimal("30"),
				new BigDecimal("400"));

		assertThat(riepilogo.profittoNetto()).isEqualByComparingTo("50");
		assertThat(riepilogo.roiNetto()).isEqualByComparingTo("33.3");
	}

}
