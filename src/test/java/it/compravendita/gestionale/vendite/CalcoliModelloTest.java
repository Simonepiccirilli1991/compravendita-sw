package it.compravendita.gestionale.vendite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.Brand;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import it.compravendita.gestionale.piattaforme.Commissioni;
import it.compravendita.gestionale.piattaforme.Piattaforma;

/**
 * Calcoli del modello di dominio, senza database.
 */
class CalcoliModelloTest {

	private final Articolo charizard = new Articolo("Charizard ex 199/165", Categoria.CARTA_SINGOLA,
			new Brand("Pokémon"));

	@Test
	void costoUnitarioEffettivoIncludeCostiAccessoriEGrading() {
		RigaAcquisto riga = new RigaAcquisto(charizard, 4, new BigDecimal("10.00"));
		assertThat(riga.getCostoUnitarioEffettivo()).isEqualByComparingTo("10");

		riga.setQuotaCostiAccessori(new BigDecimal("6.00"));
		assertThat(riga.getCostoUnitarioEffettivo()).isEqualByComparingTo("11.5");

		riga.aggiungiCostoAggiuntivo(new BigDecimal("2.00"));
		assertThat(riga.getCostoTotale()).isEqualByComparingTo("48");
		assertThat(riga.getCostoUnitarioEffettivo()).isEqualByComparingTo("12");
	}

	@Test
	void totaliAcquisto() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 10, 1));
		acquisto.setCostoSpedizione(new BigDecimal("7.50"));
		acquisto.setCommissioni(new BigDecimal("1.20"));
		acquisto.aggiungiRiga(new RigaAcquisto(charizard, 2, new BigDecimal("15.00")));
		acquisto.aggiungiRiga(new RigaAcquisto(charizard, 1, new BigDecimal("30.00")));

		assertThat(acquisto.getTotaleMerce()).isEqualByComparingTo("60");
		assertThat(acquisto.getTotaleCostiAccessori()).isEqualByComparingTo("8.70");
		assertThat(acquisto.getTotale()).isEqualByComparingTo("68.70");
	}

	@Test
	void nettoVendita() {
		Vendita vendita = new Vendita(LocalDate.of(2026, 10, 10), new Piattaforma("Cardmarket"));
		vendita.aggiungiRiga(new RigaVendita(charizard, 2, new BigDecimal("25.00")));
		vendita.setSpedizioneIncassata(new BigDecimal("3.00"));
		vendita.setCommissioniPiattaforma(new BigDecimal("2.50"));
		vendita.setCommissioniPagamento(new BigDecimal("0.50"));
		vendita.setCostoSpedizione(new BigDecimal("2.80"));
		vendita.setCostoImballaggio(new BigDecimal("0.70"));

		assertThat(vendita.getTotaleLordo()).isEqualByComparingTo("50");
		// 50 + 3 - 2.50 - 0.50 - 2.80 - 0.70
		assertThat(vendita.getNetto()).isEqualByComparingTo("46.50");
	}

	@Test
	void allocazioneSuPiuLottiCalcolaCostoVenduto() {
		RigaAcquisto lottoVecchio = new RigaAcquisto(charizard, 1, new BigDecimal("10.00"));
		RigaAcquisto lottoNuovo = new RigaAcquisto(charizard, 3, new BigDecimal("14.00"));
		RigaVendita riga = new RigaVendita(charizard, 3, new BigDecimal("25.00"));

		riga.allocaDa(lottoVecchio, 1);
		riga.allocaDa(lottoNuovo, 2);

		assertThat(riga.getCostoVenduto()).isEqualByComparingTo("38");
		assertThat(lottoVecchio.getQuantitaResidua()).isZero();
		assertThat(lottoVecchio.getStato()).isEqualTo(StatoOggetto.VENDUTO);
		assertThat(lottoNuovo.getQuantitaResidua()).isEqualTo(1);
	}

	@Test
	void nonSiPuoAllocarePiuDelDisponibile() {
		RigaAcquisto lotto = new RigaAcquisto(charizard, 1, new BigDecimal("10.00"));
		RigaVendita riga = new RigaVendita(charizard, 2, new BigDecimal("25.00"));

		assertThatThrownBy(() -> riga.allocaDa(lotto, 2)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void restituzioneRiportaInMagazzino() {
		RigaAcquisto lotto = new RigaAcquisto(charizard, 1, new BigDecimal("10.00"));
		lotto.preleva(1);
		lotto.restituisci(1);

		assertThat(lotto.getQuantitaResidua()).isEqualTo(1);
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
	}

	@Test
	void commissioniDellaPiattaforma() {
		Piattaforma ebay = new Piattaforma("eBay");
		ebay.setFeePercentuale(new BigDecimal("12.8"));
		ebay.setFeeFissa(new BigDecimal("0.35"));
		ebay.setFeePagamentoPercentuale(new BigDecimal("1"));

		Commissioni commissioni = ebay.calcolaCommissioni(new BigDecimal("100.00"));

		assertThat(commissioni.piattaforma()).isEqualByComparingTo("13.15");
		assertThat(commissioni.pagamento()).isEqualByComparingTo("1.00");
		assertThat(ebay.calcolaCommissioni(BigDecimal.ZERO).totale()).isEqualByComparingTo("0");
		assertThat(ebay.isSenzaCommissioni()).isFalse();
		assertThat(new Piattaforma("Fiera").isSenzaCommissioni()).isTrue();
	}

	@Test
	void profittoRoiEStorni() {
		RigaAcquisto lotto = new RigaAcquisto(charizard, 2, new BigDecimal("20.00"));
		Vendita vendita = new Vendita(LocalDate.of(2026, 10, 10), new Piattaforma("Vinted"));
		RigaVendita riga = new RigaVendita(charizard, 2, new BigDecimal("30.00"));
		riga.allocaDa(lotto, 2);
		vendita.aggiungiRiga(riga);
		vendita.setCostoSpedizione(new BigDecimal("5.00"));

		assertThat(vendita.getProfitto()).isEqualByComparingTo("15");
		assertThat(vendita.getRoi()).isEqualByComparingTo("37.5");
		assertThat(vendita.getMargine()).isEqualByComparingTo("25");

		vendita.registraReso(LocalDate.of(2026, 10, 20), new BigDecimal("7.50"), "Non conforme");

		assertThat(vendita.getStato()).isEqualTo(StatoVendita.RESA);
		assertThat(lotto.getQuantitaResidua()).isEqualTo(2);
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
		assertThat(vendita.getProfitto()).isEqualByComparingTo("-7.50");
		assertThat(vendita.getRoi()).isNull();
		assertThatThrownBy(() -> vendita.annulla(LocalDate.now(), null)).isInstanceOf(IllegalStateException.class);
	}

}
