package it.compravendita.gestionale.importazione;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.Condizione;
import it.compravendita.gestionale.magazzino.StatoOggetto;

/**
 * Una riga del file d'importazione già interpretata, con gli eventuali errori.
 *
 * @param riga numero di riga nel file (l'intestazione è la 1)
 * @param articoloId articolo esistente a cui si aggiungono i pezzi, null se va creato
 */
public record RigaImport(int riga, String nome, Categoria categoria, String brand, String lingua,
		Condizione condizione, Integer quantita, BigDecimal prezzoUnitario, LocalDate dataAcquisto, String fornitore,
		StatoOggetto stato, String note, Map<String, Object> attributi, Long articoloId, boolean brandNuovo,
		List<String> errori) {

	public RigaImport {
		errori = new ArrayList<>(errori);
	}

	public boolean isValida() {
		return errori.isEmpty();
	}

	public boolean isArticoloNuovo() {
		return articoloId == null;
	}

	public BigDecimal totale() {
		return quantita == null || prezzoUnitario == null ? null
				: prezzoUnitario.multiply(BigDecimal.valueOf(quantita));
	}

}
