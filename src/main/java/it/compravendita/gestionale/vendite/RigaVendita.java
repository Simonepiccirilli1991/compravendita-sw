package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.common.AbstractEntity;
import it.compravendita.gestionale.common.Importi;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "riga_vendita")
public class RigaVendita extends AbstractEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vendita_id", nullable = false)
	private Vendita vendita;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "articolo_id", nullable = false)
	private Articolo articolo;

	@Column(nullable = false)
	private int quantita;

	@Column(name = "prezzo_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal prezzoUnitario;

	@OneToMany(mappedBy = "rigaVendita", cascade = CascadeType.ALL, orphanRemoval = true)
	@BatchSize(size = 50)
	private List<AllocazioneVendita> allocazioni = new ArrayList<>();

	protected RigaVendita() {
	}

	public RigaVendita(Articolo articolo, int quantita, BigDecimal prezzoUnitario) {
		if (quantita <= 0) {
			throw new IllegalArgumentException("La quantità deve essere maggiore di zero");
		}
		this.articolo = articolo;
		this.quantita = quantita;
		this.prezzoUnitario = Importi.importo(prezzoUnitario);
	}

	/**
	 * Preleva i pezzi dal lotto indicato e registra il costo unitario del lotto in quel momento.
	 */
	public AllocazioneVendita allocaDa(RigaAcquisto lotto, int pezzi) {
		if (getQuantitaAllocata() + pezzi > quantita) {
			throw new IllegalArgumentException("Allocazione superiore alla quantità venduta");
		}
		lotto.preleva(pezzi);
		AllocazioneVendita allocazione = new AllocazioneVendita(this, lotto, pezzi, lotto.getCostoUnitarioEffettivo());
		allocazioni.add(allocazione);
		return allocazione;
	}

	/** Rimette nei lotti d'origine i pezzi allocati. */
	void restituisciMerce() {
		allocazioni.forEach(a -> a.getRigaAcquisto().restituisci(a.getQuantita()));
	}

	public int getQuantitaAllocata() {
		return allocazioni.stream().mapToInt(AllocazioneVendita::getQuantita).sum();
	}

	/** Prezzo unitario x quantità. */
	public BigDecimal getTotale() {
		return prezzoUnitario.multiply(BigDecimal.valueOf(quantita));
	}

	public BigDecimal getCostoVenduto() {
		return allocazioni.stream().map(AllocazioneVendita::getCostoTotale).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public Vendita getVendita() {
		return vendita;
	}

	void setVendita(Vendita vendita) {
		this.vendita = vendita;
	}

	public Articolo getArticolo() {
		return articolo;
	}

	public int getQuantita() {
		return quantita;
	}

	public BigDecimal getPrezzoUnitario() {
		return prezzoUnitario;
	}

	public List<AllocazioneVendita> getAllocazioni() {
		return Collections.unmodifiableList(allocazioni);
	}

}
