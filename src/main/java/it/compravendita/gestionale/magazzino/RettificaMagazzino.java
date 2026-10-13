package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
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
 * Scarico di pezzi da un lotto senza vendita (perso, danneggiato, uso personale, reso).
 * Il valore perso è {@code quantita x costoUnitario - importoRecuperato}.
 */
@Entity
@Table(name = "rettifica_magazzino")
public class RettificaMagazzino extends AuditedEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "riga_acquisto_id", nullable = false)
	private RigaAcquisto rigaAcquisto;

	@Column(name = "data_rettifica", nullable = false)
	private LocalDate dataRettifica;

	@Column(nullable = false)
	private int quantita;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MotivoRettifica motivo;

	@Column(name = "costo_unitario", nullable = false, precision = 14, scale = 4)
	private BigDecimal costoUnitario;

	@Column(name = "importo_recuperato", nullable = false, precision = 12, scale = 2)
	private BigDecimal importoRecuperato = BigDecimal.ZERO;

	private String note;

	protected RettificaMagazzino() {
	}

	/** Registra la rettifica togliendo i pezzi dal lotto. */
	public RettificaMagazzino(RigaAcquisto lotto, LocalDate data, int quantita, MotivoRettifica motivo,
			BigDecimal importoRecuperato, String note) {
		lotto.scarica(quantita, motivo.statoSeEsaurito());
		this.rigaAcquisto = lotto;
		this.dataRettifica = data;
		this.quantita = quantita;
		this.motivo = motivo;
		this.costoUnitario = lotto.getCostoUnitarioEffettivo();
		this.importoRecuperato = Importi.importo(importoRecuperato);
		this.note = note;
	}

	/** Rimette i pezzi nel lotto: da chiamare prima di eliminare la rettifica. */
	public void annulla() {
		rigaAcquisto.restituisci(quantita);
	}

	public BigDecimal getValore() {
		return Importi.importo(costoUnitario.multiply(BigDecimal.valueOf(quantita)));
	}

	/** Valore a costo dei pezzi meno quanto recuperato. */
	public BigDecimal getPerdita() {
		return getValore().subtract(importoRecuperato);
	}

	public RigaAcquisto getRigaAcquisto() {
		return rigaAcquisto;
	}

	public LocalDate getDataRettifica() {
		return dataRettifica;
	}

	public int getQuantita() {
		return quantita;
	}

	public MotivoRettifica getMotivo() {
		return motivo;
	}

	public BigDecimal getCostoUnitario() {
		return costoUnitario;
	}

	public BigDecimal getImportoRecuperato() {
		return importoRecuperato;
	}

	public String getNote() {
		return note;
	}

}
