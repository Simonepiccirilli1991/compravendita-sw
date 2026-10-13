package it.compravendita.gestionale.piattaforme;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class PiattaformaForm {

	@NotBlank(message = "Il nome è obbligatorio")
	@Size(max = 100, message = "Massimo 100 caratteri")
	private String nome;

	@NotNull(message = "Indica la percentuale (0 se non prevista)")
	@PositiveOrZero(message = "Non può essere negativa")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal feePercentuale = BigDecimal.ZERO;

	@NotNull(message = "Indica l'importo (0 se non previsto)")
	@PositiveOrZero(message = "Non può essere negativa")
	private BigDecimal feeFissa = BigDecimal.ZERO;

	@NotNull(message = "Indica la percentuale (0 se non prevista)")
	@PositiveOrZero(message = "Non può essere negativa")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal feePagamentoPercentuale = BigDecimal.ZERO;

	@NotNull(message = "Indica l'importo (0 se non previsto)")
	@PositiveOrZero(message = "Non può essere negativa")
	private BigDecimal feePagamentoFissa = BigDecimal.ZERO;

	private boolean attiva = true;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	public static PiattaformaForm da(Piattaforma p) {
		PiattaformaForm form = new PiattaformaForm();
		form.nome = p.getNome();
		form.feePercentuale = p.getFeePercentuale();
		form.feeFissa = p.getFeeFissa();
		form.feePagamentoPercentuale = p.getFeePagamentoPercentuale();
		form.feePagamentoFissa = p.getFeePagamentoFissa();
		form.attiva = p.isAttiva();
		form.note = p.getNote();
		return form;
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
