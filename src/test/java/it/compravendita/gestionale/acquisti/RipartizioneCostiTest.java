package it.compravendita.gestionale.acquisti;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.magazzino.StatoOggetto;

class RipartizioneCostiTest {

	private final Articolo carta = new Articolo("Pikachu", Categoria.TAZZA, null);

	private final Articolo lego = new Articolo("Millennium Falcon", Categoria.TAZZA_TERMICA, null);

	@Test
	void ripartizioneProporzionaleAlPrezzo() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto carte = new RigaAcquisto(carta, 10, new BigDecimal("5.00"));
		RigaAcquisto set = new RigaAcquisto(lego, 1, new BigDecimal("150.00"));
		acquisto.aggiungiRiga(carte);
		acquisto.aggiungiRiga(set);
		acquisto.setCostoSpedizione(new BigDecimal("20.00"));

		acquisto.ripartisciCostiAccessori();

		// merce 50 + 150 = 200: 25% alle carte, 75% al LEGO
		assertThat(carte.getQuotaCostiAccessori()).isEqualByComparingTo("5.00");
		assertThat(set.getQuotaCostiAccessori()).isEqualByComparingTo("15.00");
		assertThat(carte.getCostoUnitarioEffettivo()).isEqualByComparingTo("5.50");
		assertThat(set.getCostoUnitarioEffettivo()).isEqualByComparingTo("165.00");
		assertThat(acquisto.getTotale()).isEqualByComparingTo("220.00");
	}

	@Test
	void ripartizionePerQuantita() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto carte = new RigaAcquisto(carta, 3, new BigDecimal("1.00"));
		RigaAcquisto set = new RigaAcquisto(lego, 1, new BigDecimal("100.00"));
		acquisto.aggiungiRiga(carte);
		acquisto.aggiungiRiga(set);
		acquisto.setCostoSpedizione(new BigDecimal("8.00"));
		acquisto.setMetodoRipartizione(MetodoRipartizione.QUANTITA);

		acquisto.ripartisciCostiAccessori();

		assertThat(carte.getQuotaCostiAccessori()).isEqualByComparingTo("6.00");
		assertThat(set.getQuotaCostiAccessori()).isEqualByComparingTo("2.00");
		assertThat(carte.getCostoUnitarioEffettivo()).isEqualByComparingTo("3.00");
	}

	@Test
	void centesimiDiArrotondamentoAllaRigaPiuPesante() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto a = new RigaAcquisto(carta, 1, new BigDecimal("10.00"));
		RigaAcquisto b = new RigaAcquisto(carta, 1, new BigDecimal("10.00"));
		RigaAcquisto c = new RigaAcquisto(lego, 1, new BigDecimal("10.01"));
		acquisto.aggiungiRiga(a);
		acquisto.aggiungiRiga(b);
		acquisto.aggiungiRiga(c);
		acquisto.setCostoSpedizione(new BigDecimal("10.00"));

		acquisto.ripartisciCostiAccessori();

		BigDecimal somma = a.getQuotaCostiAccessori().add(b.getQuotaCostiAccessori()).add(c.getQuotaCostiAccessori());
		assertThat(somma).isEqualByComparingTo("10.00");
		assertThat(c.getQuotaCostiAccessori()).isEqualByComparingTo("3.34");
	}

	@Test
	void prezziTuttiAZeroRipartitiPerQuantita() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto omaggio = new RigaAcquisto(carta, 4, BigDecimal.ZERO);
		acquisto.aggiungiRiga(omaggio);
		acquisto.setAltriCosti(new BigDecimal("2.00"));

		acquisto.ripartisciCostiAccessori();

		assertThat(omaggio.getCostoUnitarioEffettivo()).isEqualByComparingTo("0.50");
	}

	@Test
	void senzaCostiAccessoriIlCostoEIlPrezzo() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto riga = new RigaAcquisto(carta, 2, new BigDecimal("7.30"));
		acquisto.aggiungiRiga(riga);

		acquisto.ripartisciCostiAccessori();

		assertThat(riga.getQuotaCostiAccessori()).isEqualByComparingTo("0");
		assertThat(riga.getCostoUnitarioEffettivo()).isEqualByComparingTo("7.30");
	}

	@Test
	void scorporoPezzoMantieneIlCostoUnitario() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto carte = new RigaAcquisto(carta, 4, new BigDecimal("10.00"));
		acquisto.aggiungiRiga(carte);
		acquisto.setCostoSpedizione(new BigDecimal("4.00"));
		acquisto.ripartisciCostiAccessori();
		carte.preleva(1);

		RigaAcquisto pezzo = acquisto.scorporaPezzo(carte);

		assertThat(acquisto.getRighe()).hasSize(2);
		assertThat(pezzo.getQuantita()).isEqualTo(1);
		assertThat(pezzo.getCostoUnitarioEffettivo()).isEqualByComparingTo("11.00");
		assertThat(carte.getQuantita()).isEqualTo(3);
		assertThat(carte.getQuantitaResidua()).isEqualTo(2);
		assertThat(carte.getCostoUnitarioEffettivo()).isEqualByComparingTo("11.00");
		assertThat(acquisto.getTotale()).isEqualByComparingTo("44.00");

		// una nuova ripartizione dà lo stesso risultato
		acquisto.ripartisciCostiAccessori();
		assertThat(pezzo.getCostoUnitarioEffettivo()).isEqualByComparingTo("11.00");
	}

	@Test
	void scorporoDiUnLottoDaUnPezzoRestituisceLoStesso() {
		Acquisto acquisto = new Acquisto(LocalDate.of(2026, 5, 1));
		RigaAcquisto singola = new RigaAcquisto(carta, 1, new BigDecimal("10.00"));
		acquisto.aggiungiRiga(singola);

		assertThat(acquisto.scorporaPezzo(singola)).isSameAs(singola);
		assertThat(acquisto.getRighe()).hasSize(1);
	}

	@Test
	void lottoMovimentatoNonModificabile() {
		RigaAcquisto lotto = new RigaAcquisto(carta, 2, new BigDecimal("10.00"));
		assertThat(lotto.isMovimentato()).isFalse();

		lotto.scarica(2, StatoOggetto.SCARICATO);

		assertThat(lotto.isMovimentato()).isTrue();
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.SCARICATO);
		assertThatThrownBy(() -> lotto.modifica(carta, 3, BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);

		lotto.restituisci(2);
		assertThat(lotto.getStato()).isEqualTo(StatoOggetto.IN_STOCK);
	}

}
