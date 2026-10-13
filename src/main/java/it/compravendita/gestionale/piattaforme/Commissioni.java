package it.compravendita.gestionale.piattaforme;

import java.math.BigDecimal;

/**
 * Commissioni di un ordine: quella della piattaforma e quella del sistema di pagamento.
 */
public record Commissioni(BigDecimal piattaforma, BigDecimal pagamento) {

	public BigDecimal totale() {
		return piattaforma.add(pagamento);
	}

}
