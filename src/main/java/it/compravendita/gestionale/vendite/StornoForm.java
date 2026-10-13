package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Annullamento o reso di una vendita. {@code costiReso} vale solo per il reso.
 */
public class StornoForm {

	@NotNull(message = "Indica la data")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate data = LocalDate.now();

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String motivo;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costiReso;

	public LocalDate getData() {
		return data;
	}

	public void setData(LocalDate data) {
		this.data = data;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public BigDecimal getCostiReso() {
		return costiReso;
	}

	public void setCostiReso(BigDecimal costiReso) {
		this.costiReso = costiReso;
	}

}
