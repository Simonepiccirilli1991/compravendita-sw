package it.compravendita.gestionale.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Impostazioni della versione installata, nel file {@code gestionale.properties} della cartella dati.
 * Il file si modifica con il Blocco note: righe {@code chiave=valore}, i backslash dei percorsi
 * Windows vanno bene così come sono (non è un file .properties di Java, che li tratterebbe come escape).
 *
 * @param porta porta preferita della pagina web; se è occupata se ne usa un'altra libera
 * @param utente nome utente per l'accesso
 * @param password password per l'accesso
 * @param cartellaBackup dove salvare i backup
 */
public record ConfigurazioneDesktop(int porta, String utente, String password, Path cartellaBackup) {

	public static final int PORTA_PREDEFINITA = 8181;

	public static final String UTENTE_PREDEFINITO = "admin";

	public static final String PASSWORD_PREDEFINITA = "admin";

	/** Legge il file; le chiavi mancanti o non valide prendono il valore predefinito. */
	public static ConfigurazioneDesktop leggi(Path file, Path backupPredefinito) throws IOException {
		Map<String, String> valori = Files.exists(file) ? analizza(Files.readString(file, StandardCharsets.UTF_8))
				: Map.of();
		int porta = PORTA_PREDEFINITA;
		try {
			int indicata = Integer.parseInt(valori.getOrDefault("porta", "").trim());
			if (indicata > 0 && indicata < 65536) {
				porta = indicata;
			}
		}
		catch (NumberFormatException ex) {
			// resta la porta predefinita
		}
		String utente = nonVuoto(valori.get("utente"), UTENTE_PREDEFINITO);
		String password = nonVuoto(valori.get("password"), PASSWORD_PREDEFINITA);
		String backup = valori.get("cartella_backup");
		Path cartellaBackup = backup == null || backup.isBlank() ? backupPredefinito : Path.of(backup.trim());
		return new ConfigurazioneDesktop(porta, utente, password, cartellaBackup);
	}

	public void scrivi(Path file) throws IOException {
		String testo = """
				# Impostazioni di Gestionale Tazze. Dopo una modifica chiudi e riapri il programma.

				# Porta della pagina web (http://127.0.0.1:porta). Se è occupata ne viene scelta un'altra.
				porta=%d

				# Credenziali per entrare nel gestionale
				utente=%s
				password=%s

				# Cartella dove vengono salvati i backup
				cartella_backup=%s
				""".formatted(porta, utente, password, cartellaBackup);
		Files.writeString(file, testo.replace("\n", System.lineSeparator()), StandardCharsets.UTF_8);
	}

	public ConfigurazioneDesktop conCredenziali(String utente, String password) {
		return new ConfigurazioneDesktop(porta, utente, password, cartellaBackup);
	}

	static Map<String, String> analizza(String testo) {
		Map<String, String> valori = new LinkedHashMap<>();
		for (String riga : testo.split("\\R")) {
			String pulita = riga.strip();
			if (pulita.startsWith("﻿")) {
				pulita = pulita.substring(1);
			}
			int uguale = pulita.indexOf('=');
			if (pulita.isEmpty() || pulita.startsWith("#") || uguale <= 0) {
				continue;
			}
			valori.put(pulita.substring(0, uguale).strip().toLowerCase(), pulita.substring(uguale + 1).strip());
		}
		return valori;
	}

	private static String nonVuoto(String valore, String predefinito) {
		return valore == null || valore.isBlank() ? predefinito : valore.strip();
	}

}
