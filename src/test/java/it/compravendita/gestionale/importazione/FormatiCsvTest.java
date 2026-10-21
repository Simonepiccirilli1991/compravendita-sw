package it.compravendita.gestionale.importazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.Condizione;
import it.compravendita.gestionale.esportazioni.CsvScrittore;
import it.compravendita.gestionale.esportazioni.Tabella;

class FormatiCsvTest {

	@Test
	void csvPerExcelItaliano() {
		Tabella tabella = new Tabella("Prova").testo("Nome").importo("Prezzo").data("Data").intero("Pezzi")
			.riga("Carta; \"rara\"", new BigDecimal("1234.5"), LocalDate.of(2026, 3, 1), 2)
			.riga(null, null, null, null);

		String csv = new String(CsvScrittore.scrivi(tabella), StandardCharsets.UTF_8);

		assertThat(csv).startsWith("﻿Nome;Prezzo;Data;Pezzi\r\n");
		assertThat(csv).contains("\"Carta; \"\"rara\"\"\";1234,50;01/03/2026;2\r\n");
		assertThat(csv).endsWith(";;;\r\n");
	}

	@Test
	void letturaConVirgoletteSeparatoriEBom() {
		String testo = "﻿nome;note\r\n\"Carta; rara\";\"riga 1\nriga 2\"\n;\n\"con \"\"virgolette\"\"\";x";

		var righe = LettoreCsv.leggi(testo.getBytes(StandardCharsets.UTF_8));

		assertThat(righe).hasSize(3);
		assertThat(righe.get(1)).containsExactly("Carta; rara", "riga 1\nriga 2");
		assertThat(righe.get(2)).containsExactly("con \"virgolette\"", "x");
	}

	@Test
	void separatoreVirgola() {
		var righe = LettoreCsv.leggi("nome,quantita\nZelda,2\n".getBytes(StandardCharsets.UTF_8));

		assertThat(righe.get(1)).containsExactly("Zelda", "2");
	}

	@Test
	void conversioniDiValori() {
		assertThat(ImportazioneService.numero("1.234,50")).isEqualByComparingTo("1234.50");
		assertThat(ImportazioneService.numero("12,5 €")).isEqualByComparingTo("12.5");
		assertThat(ImportazioneService.numero("99.90")).isEqualByComparingTo("99.90");
		assertThat(ImportazioneService.numero("dieci")).isNull();
		assertThat(ImportazioneService.data("5/3/2026")).isEqualTo(LocalDate.of(2026, 3, 5));
		assertThat(ImportazioneService.data("2026-03-05")).isEqualTo(LocalDate.of(2026, 3, 5));
		assertThat(ImportazioneService.data("32/13/2026")).isNull();
		assertThat(ImportazioneService.normalizza(" Quantità ")).isEqualTo("quantita");
		assertThat(ImportazioneService.normalizza("Prezzo unitario")).isEqualTo("prezzo_unitario");
		assertThat(ImportazioneService.enumDa(Categoria.values(), "tazza termica / borraccia", Categoria::getEtichetta))
			.isEqualTo(Categoria.TAZZA_TERMICA);
		assertThat(ImportazioneService.enumDa(Categoria.values(), "SET_REGALO", Categoria::getEtichetta))
			.isEqualTo(Categoria.SET_REGALO);
		assertThat(ImportazioneService.enumDa(Condizione.values(), "come nuovo", Condizione::getEtichetta))
			.isEqualTo(Condizione.COME_NUOVO);
		assertThat(ImportazioneService.enumDa(Categoria.values(), "boh", Categoria::getEtichetta)).isNull();
	}

}
