package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class InvioGradingForm {

	@NotBlank(message = "Scegli l'ente di grading")
	private String ente;

	@Size(max = 100, message = "Massimo 100 caratteri")
	private String servizio;

	@Size(max = 100, message = "Massimo 100 caratteri")
	private String numeroOrdine;

	@NotNull(message = "Indica la data di invio")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dataInvio = LocalDate.now();

	/** Costo previsto (grading + spedizioni), confermato al rientro. */
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoPrevisto;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	public String getEnte() {
		return ente;
	}

	public void setEnte(String ente) {
		this.ente = ente;
	}

	public String getServizio() {
		return servizio;
	}

	public void setServizio(String servizio) {
		this.servizio = servizio;
	}

	public String getNumeroOrdine() {
		return numeroOrdine;
	}

	public void setNumeroOrdine(String numeroOrdine) {
		this.numeroOrdine = numeroOrdine;
	}

	public LocalDate getDataInvio() {
		return dataInvio;
	}

	public void setDataInvio(LocalDate dataInvio) {
		this.dataInvio = dataInvio;
	}

	public BigDecimal getCostoPrevisto() {
		return costoPrevisto;
	}

	public void setCostoPrevisto(BigDecimal costoPrevisto) {
		this.costoPrevisto = costoPrevisto;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
