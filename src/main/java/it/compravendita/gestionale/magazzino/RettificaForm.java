package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class RettificaForm {

	@NotNull(message = "Indica la data")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate data = LocalDate.now();

	@NotNull(message = "Indica i pezzi")
	@Min(value = 1, message = "Almeno 1")
	private Integer quantita = 1;

	@NotNull(message = "Scegli il motivo")
	private MotivoRettifica motivo;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal importoRecuperato;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	public LocalDate getData() {
		return data;
	}

	public void setData(LocalDate data) {
		this.data = data;
	}

	public Integer getQuantita() {
		return quantita;
	}

	public void setQuantita(Integer quantita) {
		this.quantita = quantita;
	}

	public MotivoRettifica getMotivo() {
		return motivo;
	}

	public void setMotivo(MotivoRettifica motivo) {
		this.motivo = motivo;
	}

	public BigDecimal getImportoRecuperato() {
		return importoRecuperato;
	}

	public void setImportoRecuperato(BigDecimal importoRecuperato) {
		this.importoRecuperato = importoRecuperato;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
