package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.common.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Quanti pezzi di una riga di vendita provengono da un certo lotto d'acquisto, e a che costo.
 */
@Entity
@Table(name = "allocazione_vendita")
public class AllocazioneVendita extends AbstractEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "riga_vendita_id", nullable = false)
	private RigaVendita rigaVendita;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "riga_acquisto_id", nullable = false)
	private RigaAcquisto rigaAcquisto;

	@Column(nullable = false)
	private int quantita;

	@Column(name = "costo_unitario", nullable = false, precision = 14, scale = 4)
	private BigDecimal costoUnitario;

	protected AllocazioneVendita() {
	}

	AllocazioneVendita(RigaVendita rigaVendita, RigaAcquisto rigaAcquisto, int quantita, BigDecimal costoUnitario) {
		this.rigaVendita = rigaVendita;
		this.rigaAcquisto = rigaAcquisto;
		this.quantita = quantita;
		this.costoUnitario = costoUnitario;
	}

	public BigDecimal getCostoTotale() {
		return costoUnitario.multiply(BigDecimal.valueOf(quantita));
	}

	public RigaVendita getRigaVendita() {
		return rigaVendita;
	}

	public RigaAcquisto getRigaAcquisto() {
		return rigaAcquisto;
	}

	public int getQuantita() {
		return quantita;
	}

	public BigDecimal getCostoUnitario() {
		return costoUnitario;
	}

}
