package it.compravendita.gestionale.esportazioni;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.esportazioni.Tabella.Colonna;

/**
 * CSV pensato per Excel in italiano: separatore ";", virgola decimale, date gg/mm/aaaa,
 * codifica UTF-8 con BOM (così le lettere accentate si leggono correttamente).
 */
public final class CsvScrittore {

	public static final String SEPARATORE = ";";

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private CsvScrittore() {
	}

	public static byte[] scrivi(Tabella tabella) {
		StringBuilder csv = new StringBuilder("﻿");
		csv.append(tabella.getColonne().stream().map(c -> campo(c.titolo())).collect(Collectors.joining(SEPARATORE)))
			.append("\r\n");
		List<Colonna> colonne = tabella.getColonne();
		for (List<Object> riga : tabella.getRighe()) {
			for (int i = 0; i < riga.size(); i++) {
				if (i > 0) {
					csv.append(SEPARATORE);
				}
				csv.append(valore(riga.get(i), colonne.get(i).tipo()));
			}
			csv.append("\r\n");
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static String valore(Object valore, Tabella.Tipo tipo) {
		if (valore == null) {
			return "";
		}
		return switch (valore) {
			case BigDecimal numero -> (tipo == Tabella.Tipo.IMPORTO ? Importi.importo(numero) : numero.stripTrailingZeros())
				.toPlainString()
				.replace('.', ',');
			case Number numero -> numero.toString();
			case LocalDate data -> DATA.format(data);
			case Boolean b -> b ? "Sì" : "No";
			default -> campo(valore.toString());
		};
	}

	/** Racchiude tra virgolette i testi con separatori, virgolette o a capo. */
	public static String campo(String testo) {
		if (testo.contains(SEPARATORE) || testo.contains("\"") || testo.contains("\n") || testo.contains("\r")) {
			return "\"" + testo.replace("\"", "\"\"") + "\"";
		}
		return testo;
	}

}
