package it.compravendita.gestionale.catalogo;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dati del form di creazione/modifica articolo. Gli attributi di categoria arrivano come
 * stringhe e vengono validati/convertiti da {@link SchemaAttributi}.
 */
public class ArticoloForm {

	@NotBlank(message = "Inserisci il nome")
	@Size(max = 255, message = "Massimo 255 caratteri")
	private String nome;

	@NotNull(message = "Scegli la categoria")
	private Categoria categoria;

	private Long brandId;

	@Size(max = 30, message = "Massimo 30 caratteri")
	private String lingua;

	private Condizione condizione;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	private Map<String, String> attributi = new HashMap<>();

	private boolean rimuoviFoto;

	public static ArticoloForm da(Articolo articolo, SchemaAttributi schema) {
		ArticoloForm form = new ArticoloForm();
		form.nome = articolo.getNome();
		form.categoria = articolo.getCategoria();
		form.brandId = articolo.getBrand() != null ? articolo.getBrand().getId() : null;
		form.lingua = articolo.getLingua();
		form.condizione = articolo.getCondizione();
		form.note = articolo.getNote();
		form.attributi = new HashMap<>(schema.valoriPerForm(articolo.getAttributi()));
		return form;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public Long getBrandId() {
		return brandId;
	}

	public void setBrandId(Long brandId) {
		this.brandId = brandId;
	}

	public String getLingua() {
		return lingua;
	}

	public void setLingua(String lingua) {
		this.lingua = lingua;
	}

	public Condizione getCondizione() {
		return condizione;
	}

	public void setCondizione(Condizione condizione) {
		this.condizione = condizione;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Map<String, String> getAttributi() {
		return attributi;
	}

	public void setAttributi(Map<String, String> attributi) {
		this.attributi = attributi != null ? attributi : new HashMap<>();
	}

	public boolean isRimuoviFoto() {
		return rimuoviFoto;
	}

	public void setRimuoviFoto(boolean rimuoviFoto) {
		this.rimuoviFoto = rimuoviFoto;
	}

}
