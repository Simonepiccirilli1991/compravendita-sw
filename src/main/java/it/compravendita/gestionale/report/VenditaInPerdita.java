package it.compravendita.gestionale.report;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Vendita completata con profitto negativo. */
public record VenditaInPerdita(Long id, LocalDate data, String piattaforma, String articolo, BigDecimal profitto) {
}
