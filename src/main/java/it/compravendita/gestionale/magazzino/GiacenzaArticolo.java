package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.Importi;

/**
 * Giacenza aggregata di un articolo su tutti i suoi lotti con pezzi residui.
 *
 * @param primoAcquisto data del lotto più vecchio ancora in giacenza
 */
public record GiacenzaArticolo(Long articoloId, String nome, Categoria categoria, String brand, Long pezzi,
		Long inArrivo, Long inVendita, BigDecimal valore, LocalDate primoAcquisto) {

	public BigDecimal costoMedio() {
		return pezzi == null || pezzi == 0 ? BigDecimal.ZERO
				: valore.divide(BigDecimal.valueOf(pezzi), Importi.SCALA_IMPORTO, Importi.ARROTONDAMENTO);
	}

	/** Giorni trascorsi dall'acquisto del lotto più vecchio. */
	public long giorniGiacenza() {
		return primoAcquisto == null ? 0 : ChronoUnit.DAYS.between(primoAcquisto, LocalDate.now(Formati.FUSO));
	}

}
