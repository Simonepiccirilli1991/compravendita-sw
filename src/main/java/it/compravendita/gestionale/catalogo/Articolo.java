package it.compravendita.gestionale.catalogo;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import it.compravendita.gestionale.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Voce di catalogo. I dati specifici della categoria (set, numero carta, ente di grading,
 * console, numero set LEGO, ...) sono in {@link #getAttributi()}, salvati come JSONB.
 * Le chiavi standard sono in {@link AttributiArticolo}.
 */
@Entity
@Table(name = "articolo")
@BatchSize(size = 50)
public class Articolo extends AuditedEntity {

	@Column(nullable = false)
	private String nome;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Categoria categoria;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "brand_id")
	private Brand brand;

	@Column(length = 30)
	private String lingua;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Condizione condizione;

	private String note;

	@Column(name = "foto_path", length = 500)
	private String fotoPath;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> attributi = new HashMap<>();

	protected Articolo() {
	}

	public Articolo(String nome, Categoria categoria, Brand brand) {
		this.nome = nome;
		this.categoria = categoria;
		this.brand = brand;
	}

	public Object getAttributo(String chiave) {
		return attributi.get(chiave);
	}

	public void setAttributo(String chiave, Object valore) {
		if (valore == null) {
			attributi.remove(chiave);
		}
		else {
			attributi.put(chiave, valore);
		}
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

	public Brand getBrand() {
		return brand;
	}

	public void setBrand(Brand brand) {
		this.brand = brand;
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

	public String getFotoPath() {
		return fotoPath;
	}

	public void setFotoPath(String fotoPath) {
		this.fotoPath = fotoPath;
	}

	public Map<String, Object> getAttributi() {
		return attributi;
	}

	public void setAttributi(Map<String, Object> attributi) {
		this.attributi = attributi != null ? new HashMap<>(attributi) : new HashMap<>();
	}

}
