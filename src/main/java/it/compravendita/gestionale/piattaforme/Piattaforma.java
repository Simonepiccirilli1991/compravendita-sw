package it.compravendita.gestionale.piattaforme;

import java.math.BigDecimal;

import it.compravendita.gestionale.common.AuditedEntity;
import it.compravendita.gestionale.common.Importi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Canale di vendita (eBay, Vinted, Cardmarket, fiera, ...) con le relative commissioni:
 * una parte percentuale sul transato e una fissa per ordine, sia per la piattaforma
 * sia per il sistema di pagamento.
 */
@Entity
@Table(name = "piattaforma")
public class Piattaforma extends AuditedEntity {

	private static final BigDecimal CENTO = BigDecimal.valueOf(100);

	@Column(nullable = false, length = 100)
	private String nome;

	@Column(name = "fee_percentuale", nullable = false, precision = 5, scale = 2)
	private BigDecimal feePercentuale = BigDecimal.ZERO;

	@Column(name = "fee_fissa", nullable = false, precision = 12, scale = 2)
	private BigDecimal feeFissa = BigDecimal.ZERO;

	@Column(name = "fee_pagamento_percentuale", nullable = false, precision = 5, scale = 2)
	private BigDecimal feePagamentoPercentuale = BigDecimal.ZERO;

	@Column(name = "fee_pagamento_fissa", nullable = false, precision = 12, scale = 2)
	private BigDecimal feePagamentoFissa = BigDecimal.ZERO;

	@Column(nullable = false)
	private boolean attiva = true;

	private String note;

	protected Piattaforma() {
	}

	public Piattaforma(String nome) {
		this.nome = nome;
	}

	/**
	 * Commissioni previste per un ordine con l'incasso indicato (merce + spedizione pagata
	 * dall'acquirente): percentuale sull'incasso più quota fissa, per piattaforma e pagamento.
	 * Con incasso zero non ci sono commissioni.
	 */
	public Commissioni calcolaCommissioni(BigDecimal incasso) {
		if (incasso == null || incasso.signum() <= 0) {
			return new Commissioni(Importi.importo(BigDecimal.ZERO), Importi.importo(BigDecimal.ZERO));
		}
		return new Commissioni(commissione(incasso, feePercentuale, feeFissa),
				commissione(incasso, feePagamentoPercentuale, feePagamentoFissa));
	}

	private static BigDecimal commissione(BigDecimal incasso, BigDecimal percentuale, BigDecimal fissa) {
		return Importi.importo(incasso.multiply(Importi.nonNullo(percentuale))
			.divide(CENTO, Importi.SCALA_COSTO_UNITARIO, Importi.ARROTONDAMENTO)
			.add(Importi.nonNullo(fissa)));
	}

	/** Vero se la piattaforma non ha commissioni configurate. */
	public boolean isSenzaCommissioni() {
		return Importi.nonNullo(feePercentuale).signum() == 0 && Importi.nonNullo(feeFissa).signum() == 0
				&& Importi.nonNullo(feePagamentoPercentuale).signum() == 0
				&& Importi.nonNullo(feePagamentoFissa).signum() == 0;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public BigDecimal getFeePercentuale() {
		return feePercentuale;
	}

	public void setFeePercentuale(BigDecimal feePercentuale) {
		this.feePercentuale = feePercentuale;
	}

	public BigDecimal getFeeFissa() {
		return feeFissa;
	}

	public void setFeeFissa(BigDecimal feeFissa) {
		this.feeFissa = feeFissa;
	}

	public BigDecimal getFeePagamentoPercentuale() {
		return feePagamentoPercentuale;
	}

	public void setFeePagamentoPercentuale(BigDecimal feePagamentoPercentuale) {
		this.feePagamentoPercentuale = feePagamentoPercentuale;
	}

	public BigDecimal getFeePagamentoFissa() {
		return feePagamentoFissa;
	}

	public void setFeePagamentoFissa(BigDecimal feePagamentoFissa) {
		this.feePagamentoFissa = feePagamentoFissa;
	}

	public boolean isAttiva() {
		return attiva;
	}

	public void setAttiva(boolean attiva) {
		this.attiva = attiva;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
