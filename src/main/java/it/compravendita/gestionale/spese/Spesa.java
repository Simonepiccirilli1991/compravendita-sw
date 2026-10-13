package it.compravendita.gestionale.spese;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.common.AuditedEntity;
import it.compravendita.gestionale.common.Importi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Spesa dell'attività. Se collegata a una riga d'acquisto (es. grading di una carta)
 * può essere aggiunta al costo di quel lotto; altrimenti è un costo generale.
 */
@Entity
@Table(name = "spesa")
public class Spesa extends AuditedEntity {

	@Column(name = "data_spesa", nullable = false)
	private LocalDate dataSpesa;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CategoriaSpesa categoria;

	@Column(nullable = false)
	private String descrizione;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal importo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "articolo_id")
	private Articolo articolo;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "riga_acquisto_id")
	private RigaAcquisto rigaAcquisto;

	private String note;

	protected Spesa() {
	}

	public Spesa(LocalDate dataSpesa, CategoriaSpesa categoria, String descrizione, BigDecimal importo) {
		this.dataSpesa = dataSpesa;
		this.categoria = categoria;
		this.descrizione = descrizione;
		this.importo = Importi.importo(importo);
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
		this.importo = Importi.importo(importo);
	}

	public Articolo getArticolo() {
		return articolo;
	}

	public void setArticolo(Articolo articolo) {
		this.articolo = articolo;
	}

	public RigaAcquisto getRigaAcquisto() {
		return rigaAcquisto;
	}

	public void setRigaAcquisto(RigaAcquisto rigaAcquisto) {
		this.rigaAcquisto = rigaAcquisto;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
