package it.compravendita.gestionale.report;

import java.math.BigDecimal;

/**
 * Velocità di vendita: giorni medi dall'acquisto alla vendita, pesati sui pezzi venduti.
 */
public record Velocita(String chiave, String etichetta, BigDecimal giorniMedi, long pezzi) {
}
