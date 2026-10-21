package it.compravendita.gestionale.acquisti;

import java.math.BigDecimal;

import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.common.AuditedEntity;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Lotto di un articolo all'interno di un acquisto. È anche l'unità di magazzino:
 * {@link #getQuantitaResidua()} indica i pezzi ancora disponibili e il
 * {@link #getCostoUnitarioEffettivo()} è il costo usato per calcolare il profitto delle vendite.
 */
@Entity
@Table(name = "riga_acquisto")
public class RigaAcquisto extends AuditedEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "acquisto_id", nullable = false)
	private Acquisto acquisto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "articolo_id", nullable = false)
	private Articolo articolo;

	@Column(nullable = false)
	private int quantita;

	@Column(name = "prezzo_unitario", nullable = false, precision = 12, scale = 2)
	private BigDecimal prezzoUnitario;

	@Column(name = "quota_costi_accessori", nullable = false, precision = 12, scale = 2)
	private BigDecimal quotaCostiAccessori = BigDecimal.ZERO;

	@Column(name = "costi_aggiuntivi", nullable = false, precision = 12, scale = 2)
	private BigDecimal costiAggiuntivi = BigDecimal.ZERO;

	@Column(name = "costo_unitario_effettivo", nullable = false, precision = 14, scale = 4)
	private BigDecimal costoUnitarioEffettivo;

	@Column(name = "quantita_residua", nullable = false)
	private int quantitaResidua;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatoOggetto stato = StatoOggetto.IN_STOCK;

	protected RigaAcquisto() {
	}

	public RigaAcquisto(Articolo articolo, int quantita, BigDecimal prezzoUnitario) {
		if (quantita <= 0) {
			throw new IllegalArgumentException("La quantità deve essere maggiore di zero");
		}
		this.articolo = articolo;
		this.quantita = quantita;
		this.quantitaResidua = quantita;
		this.prezzoUnitario = Importi.importo(prezzoUnitario);
		ricalcolaCostoUnitarioEffettivo();
	}

	/** Prezzo unitario x quantità. */
	public BigDecimal getTotalePrezzo() {
		return prezzoUnitario.multiply(BigDecimal.valueOf(quantita));
	}

	/** Costo complessivo della riga: merce + quota costi accessori + costi aggiuntivi. */
	public BigDecimal getCostoTotale() {
		return getTotalePrezzo().add(quotaCostiAccessori).add(costiAggiuntivi);
	}

	public void ricalcolaCostoUnitarioEffettivo() {
		this.costoUnitarioEffettivo = getCostoTotale()
			.divide(BigDecimal.valueOf(quantita), Importi.SCALA_COSTO_UNITARIO, Importi.ARROTONDAMENTO);
	}

	public void setQuotaCostiAccessori(BigDecimal quotaCostiAccessori) {
		this.quotaCostiAccessori = Importi.importo(quotaCostiAccessori);
		ricalcolaCostoUnitarioEffettivo();
	}

	/** Aggiunge un costo sostenuto dopo l'acquisto (es. una spesa collegata) al costo della riga. */
	public void aggiungiCostoAggiuntivo(BigDecimal importo) {
		this.costiAggiuntivi = Importi.importo(costiAggiuntivi.add(Importi.nonNullo(importo)));
		ricalcolaCostoUnitarioEffettivo();
	}

	/** Preleva pezzi venduti dal lotto. */
	public void preleva(int pezzi) {
		scarica(pezzi, StatoOggetto.VENDUTO);
	}

	/**
	 * Toglie pezzi dal lotto (vendita o rettifica).
	 *
	 * @param statoSeEsaurito stato assunto dal lotto se non restano pezzi
	 */
	public void scarica(int pezzi, StatoOggetto statoSeEsaurito) {
		if (pezzi <= 0 || pezzi > quantitaResidua) {
			throw new IllegalArgumentException(
					"Impossibile prelevare " + pezzi + " pezzi: disponibili " + quantitaResidua);
		}
		quantitaResidua -= pezzi;
		if (quantitaResidua == 0) {
			stato = statoSeEsaurito;
		}
	}

	/** Rimette nel lotto pezzi tolti in precedenza (reso del cliente, rettifica annullata). */
	public void restituisci(int pezzi) {
		if (pezzi <= 0 || quantitaResidua + pezzi > quantita) {
			throw new IllegalArgumentException("Impossibile restituire " + pezzi + " pezzi");
		}
		quantitaResidua += pezzi;
		if (!stato.isGiacenza()) {
			stato = StatoOggetto.IN_STOCK;
		}
	}

	/**
	 * Vero se il lotto ha avuto movimenti dopo l'acquisto (vendite, rettifiche,
	 * costi aggiuntivi): in quel caso articolo, quantità e prezzo non sono più modificabili.
	 * Non considera i collegamenti registrati in altre tabelle, verificati dal repository.
	 */
	public boolean isMovimentato() {
		return quantitaResidua < quantita || costiAggiuntivi.signum() > 0
				|| !StatoOggetto.iniziali().contains(stato);
	}

	/** Modifica i dati d'acquisto di un lotto non ancora movimentato. */
	void modifica(Articolo articolo, int quantita, BigDecimal prezzoUnitario) {
		if (isMovimentato()) {
			throw new IllegalStateException("Il lotto ha movimenti e non può essere modificato");
		}
		if (quantita <= 0) {
			throw new IllegalArgumentException("La quantità deve essere maggiore di zero");
		}
		this.articolo = articolo;
		this.quantita = quantita;
		this.quantitaResidua = quantita;
		this.prezzoUnitario = Importi.importo(prezzoUnitario);
		ricalcolaCostoUnitarioEffettivo();
	}

	/**
	 * Separa un pezzo disponibile in un nuovo lotto con lo stesso costo unitario, lasciando
	 * qui i pezzi restanti. Usato da {@link Acquisto#scorporaPezzo(RigaAcquisto)}.
	 */
	RigaAcquisto separaPezzo() {
		if (quantitaResidua < 1 || quantita < 2) {
			throw new IllegalStateException("Nessun pezzo da separare");
		}
		BigDecimal quotaPezzo = quotaCostiAccessori.divide(BigDecimal.valueOf(quantita), Importi.SCALA_IMPORTO,
				Importi.ARROTONDAMENTO);
		BigDecimal aggiuntiviPezzo = costiAggiuntivi.divide(BigDecimal.valueOf(quantita), Importi.SCALA_IMPORTO,
				Importi.ARROTONDAMENTO);

		RigaAcquisto pezzo = new RigaAcquisto(articolo, 1, prezzoUnitario);
		pezzo.stato = stato;
		pezzo.quotaCostiAccessori = quotaPezzo;
		pezzo.costiAggiuntivi = aggiuntiviPezzo;
		pezzo.ricalcolaCostoUnitarioEffettivo();

		quantita -= 1;
		quantitaResidua -= 1;
		quotaCostiAccessori = quotaCostiAccessori.subtract(quotaPezzo);
		costiAggiuntivi = costiAggiuntivi.subtract(aggiuntiviPezzo);
		ricalcolaCostoUnitarioEffettivo();
		return pezzo;
	}

	public Acquisto getAcquisto() {
		return acquisto;
	}

	void setAcquisto(Acquisto acquisto) {
		this.acquisto = acquisto;
	}

	public Articolo getArticolo() {
		return articolo;
	}

	/** Valore a costo dei pezzi ancora disponibili. */
	public BigDecimal getValoreResiduo() {
		return Importi.importo(costoUnitarioEffettivo.multiply(BigDecimal.valueOf(quantitaResidua)));
	}

	public int getQuantita() {
		return quantita;
	}

	public BigDecimal getPrezzoUnitario() {
		return prezzoUnitario;
	}

	public BigDecimal getQuotaCostiAccessori() {
		return quotaCostiAccessori;
	}

	public BigDecimal getCostiAggiuntivi() {
		return costiAggiuntivi;
	}

	public BigDecimal getCostoUnitarioEffettivo() {
		return costoUnitarioEffettivo;
	}

	public int getQuantitaResidua() {
		return quantitaResidua;
	}

	public StatoOggetto getStato() {
		return stato;
	}

	public void setStato(StatoOggetto stato) {
		this.stato = stato;
	}

}
