package it.compravendita.gestionale.spese;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.catalogo.Articolo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Dati del form spesa generale. L'articolo è facoltativo e si sceglie dall'elenco (etichetta "… #id"). */
public class SpesaForm {

	@NotNull(message = "Indica la data della spesa")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dataSpesa;

	@NotNull(message = "Scegli la categoria")
	private CategoriaSpesa categoria;

	@NotBlank(message = "Inserisci una descrizione")
	@Size(max = 255, message = "Massimo 255 caratteri")
	private String descrizione;

	@NotNull(message = "Inserisci l'importo")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal importo;

	/** Testo del campo articolo; vuoto = spesa non legata a un articolo. */
	private String articoloEtichetta;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	public static SpesaForm nuova() {
		SpesaForm form = new SpesaForm();
		form.dataSpesa = LocalDate.now();
		return form;
	}

	public static SpesaForm da(Spesa spesa, Function<Articolo, String> etichetta) {
		SpesaForm form = new SpesaForm();
		form.dataSpesa = spesa.getDataSpesa();
		form.categoria = spesa.getCategoria();
		form.descrizione = spesa.getDescrizione();
		form.importo = spesa.getImporto();
		form.articoloEtichetta = spesa.getArticolo() != null ? etichetta.apply(spesa.getArticolo()) : null;
		form.note = spesa.getNote();
		return form;
	}

	public LocalDate getDataSpesa() {
		return dataSpesa;
	}

	public void setDataSpesa(LocalDate dataSpesa) {
		this.dataSpesa = dataSpesa;
	}

	public CategoriaSpesa getCategoria() {
		return categoria;
	}

	public void setCategoria(CategoriaSpesa categoria) {
		this.categoria = categoria;
	}

	public String getDescrizione() {
		return descrizione;
	}

	public void setDescrizione(String descrizione) {
		this.descrizione = descrizione;
	}

	public BigDecimal getImporto() {
		return importo;
	}

	public void setImporto(BigDecimal importo) {
		this.importo = importo;
	}

	public String getArticoloEtichetta() {
		return articoloEtichetta;
	}

	public void setArticoloEtichetta(String articoloEtichetta) {
		this.articoloEtichetta = articoloEtichetta;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
