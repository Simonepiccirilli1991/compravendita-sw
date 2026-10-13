package it.compravendita.gestionale.esportazioni;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dati tabellari da esportare: un foglio Excel o un file CSV.
 */
public final class Tabella {

	/** Come formattare i valori di una colonna. */
	public enum Tipo {

		TESTO, INTERO, DECIMALE, IMPORTO, PERCENTUALE, DATA

	}

	public record Colonna(String titolo, Tipo tipo) {
	}

	private final String nome;

	private final List<Colonna> colonne = new ArrayList<>();

	private final List<List<Object>> righe = new ArrayList<>();

	public Tabella(String nome) {
		this.nome = nome;
	}

	public Tabella colonna(String titolo, Tipo tipo) {
		colonne.add(new Colonna(titolo, tipo));
		return this;
	}

	public Tabella testo(String titolo) {
		return colonna(titolo, Tipo.TESTO);
	}

	public Tabella importo(String titolo) {
		return colonna(titolo, Tipo.IMPORTO);
	}

	public Tabella intero(String titolo) {
		return colonna(titolo, Tipo.INTERO);
	}

	public Tabella data(String titolo) {
		return colonna(titolo, Tipo.DATA);
	}

	/** Aggiunge una riga; i valori devono essere tanti quante le colonne. */
	public Tabella riga(Object... valori) {
		if (valori.length != colonne.size()) {
			throw new IllegalArgumentException(
					"Attese " + colonne.size() + " colonne, ricevute " + valori.length + " in " + nome);
		}
		righe.add(Arrays.asList(valori));
		return this;
	}

	public String getNome() {
		return nome;
	}

	public List<Colonna> getColonne() {
		return colonne;
	}

	public List<List<Object>> getRighe() {
		return righe;
	}

}
