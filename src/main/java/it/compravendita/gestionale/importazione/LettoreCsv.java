package it.compravendita.gestionale.importazione;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Lettura di file CSV: separatore ";" o "," (riconosciuto dall'intestazione), campi tra
 * virgolette con virgolette raddoppiate e a capo, BOM UTF-8 opzionale.
 */
final class LettoreCsv {

	private LettoreCsv() {
	}

	/** Righe del file, intestazione compresa; le righe completamente vuote sono scartate. */
	static List<List<String>> leggi(byte[] contenuto) {
		String testo = new String(contenuto, StandardCharsets.UTF_8);
		if (testo.startsWith("﻿")) {
			testo = testo.substring(1);
		}
		char separatore = separatore(testo);
		List<List<String>> righe = new ArrayList<>();
		List<String> riga = new ArrayList<>();
		StringBuilder campo = new StringBuilder();
		boolean traVirgolette = false;
		for (int i = 0; i < testo.length(); i++) {
			char c = testo.charAt(i);
			if (traVirgolette) {
				if (c == '"' && i + 1 < testo.length() && testo.charAt(i + 1) == '"') {
					campo.append('"');
					i++;
				}
				else if (c == '"') {
					traVirgolette = false;
				}
				else {
					campo.append(c);
				}
			}
			else if (c == '"' && campo.isEmpty()) {
				traVirgolette = true;
			}
			else if (c == separatore) {
				riga.add(campo.toString());
				campo.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < testo.length() && testo.charAt(i + 1) == '\n') {
					i++;
				}
				riga.add(campo.toString());
				campo.setLength(0);
				aggiungi(righe, riga);
				riga = new ArrayList<>();
			}
			else {
				campo.append(c);
			}
		}
		if (!campo.isEmpty() || !riga.isEmpty()) {
			riga.add(campo.toString());
			aggiungi(righe, riga);
		}
		return righe;
	}

	private static void aggiungi(List<List<String>> righe, List<String> riga) {
		if (riga.stream().anyMatch(c -> !c.isBlank())) {
			righe.add(riga);
		}
	}

	/** ";" se compare nella prima riga, altrimenti ",". */
	private static char separatore(String testo) {
		int fine = testo.indexOf('\n');
		String intestazione = fine < 0 ? testo : testo.substring(0, fine);
		return intestazione.contains(";") ? ';' : ',';
	}

}
