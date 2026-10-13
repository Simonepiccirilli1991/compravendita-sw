package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Dati del rientro dal grading: diventano attributi del nuovo articolo "carta gradata".
 */
public class RientroGradingForm {

	@NotNull(message = "Indica la data di rientro")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dataRientro = LocalDate.now();

	@NotBlank(message = "Indica il nome dell'articolo gradato")
	@Size(max = 255, message = "Massimo 255 caratteri")
	private String nomeArticolo;

	@NotNull(message = "Indica il voto")
	@DecimalMin(value = "1", message = "Deve essere tra 1 e 10")
	@DecimalMax(value = "10", message = "Deve essere tra 1 e 10")
	private BigDecimal voto;

	@NotBlank(message = "Indica il numero di certificato")
	@Size(max = 100, message = "Massimo 100 caratteri")
	private String numeroCertificato;

	@NotNull(message = "Indica il costo totale del grading")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costo;

	public LocalDate getDataRientro() {
		return dataRientro;
	}

	public void setDataRientro(LocalDate dataRientro) {
		this.dataRientro = dataRientro;
	}

	public String getNomeArticolo() {
		return nomeArticolo;
	}

	public void setNomeArticolo(String nomeArticolo) {
		this.nomeArticolo = nomeArticolo;
	}

	public BigDecimal getVoto() {
		return voto;
	}

	public void setVoto(BigDecimal voto) {
		this.voto = voto;
	}

	public String getNumeroCertificato() {
		return numeroCertificato;
	}

	public void setNumeroCertificato(String numeroCertificato) {
		this.numeroCertificato = numeroCertificato;
	}

	public BigDecimal getCosto() {
		return costo;
	}

	public void setCosto(BigDecimal costo) {
		this.costo = costo;
	}

}
