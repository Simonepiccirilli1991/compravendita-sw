package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import it.compravendita.gestionale.common.AuditedEntity;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Testata di una vendita su una piattaforma. {@link #getTotaleLordo()} e {@link #getNetto()}
 * sono salvati per velocizzare i report e vanno aggiornati con {@link #ricalcolaTotali()}.
 */
@Entity
@Table(name = "vendita")
public class Vendita extends AuditedEntity {

	@Column(name = "data_vendita", nullable = false)
	private LocalDate dataVendita;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "piattaforma_id", nullable = false)
	private Piattaforma piattaforma;

	private String acquirente;

	@Column(name = "riferimento_ordine", length = 100)
	private String riferimentoOrdine;

	@Column(name = "totale_lordo", nullable = false, precision = 12, scale = 2)
	private BigDecimal totaleLordo = BigDecimal.ZERO;

	@Column(name = "spedizione_incassata", nullable = false, precision = 12, scale = 2)
	private BigDecimal spedizioneIncassata = BigDecimal.ZERO;

	@Column(name = "commissioni_piattaforma", nullable = false, precision = 12, scale = 2)
	private BigDecimal commissioniPiattaforma = BigDecimal.ZERO;

	@Column(name = "commissioni_pagamento", nullable = false, precision = 12, scale = 2)
	private BigDecimal commissioniPagamento = BigDecimal.ZERO;

	@Column(name = "costo_spedizione", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoSpedizione = BigDecimal.ZERO;

	@Column(name = "costo_imballaggio", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoImballaggio = BigDecimal.ZERO;

	@Column(name = "altri_costi", nullable = false, precision = 12, scale = 2)
	private BigDecimal altriCosti = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal netto = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatoVendita stato = StatoVendita.COMPLETATA;

	private String note;

	@Column(name = "commissioni_automatiche", nullable = false)
	private boolean commissioniAutomatiche = true;

	@Column(name = "data_storno")
	private LocalDate dataStorno;

	@Column(name = "motivo_storno")
	private String motivoStorno;

	@Column(name = "costi_reso", nullable = false, precision = 12, scale = 2)
	private BigDecimal costiReso = BigDecimal.ZERO;

	@OneToMany(mappedBy = "vendita", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	@BatchSize(size = 50)
	private List<RigaVendita> righe = new ArrayList<>();

	protected Vendita() {
	}

	public Vendita(LocalDate dataVendita, Piattaforma piattaforma) {
		this.dataVendita = dataVendita;
		this.piattaforma = piattaforma;
	}

	public void aggiungiRiga(RigaVendita riga) {
		riga.setVendita(this);
		righe.add(riga);
		ricalcolaTotali();
	}

	public void rimuoviRiga(RigaVendita riga) {
		righe.remove(riga);
		riga.setVendita(null);
		ricalcolaTotali();
	}

	/**
	 * Lordo = somma righe; netto = lordo + spedizione incassata - commissioni - costi di
	 * spedizione, imballaggio e altri costi.
	 */
	public void ricalcolaTotali() {
		this.totaleLordo = Importi.importo(
				righe.stream().map(RigaVendita::getTotale).reduce(BigDecimal.ZERO, BigDecimal::add));
		this.netto = Importi.importo(totaleLordo
			.add(spedizioneIncassata)
			.subtract(commissioniPiattaforma)
			.subtract(commissioniPagamento)
			.subtract(costoSpedizione)
			.subtract(costoImballaggio)
			.subtract(altriCosti));
	}

	/** Rimette in magazzino tutti i pezzi prelevati dai lotti; le allocazioni restano come storico. */
	void restituisciMerce() {
		righe.forEach(RigaVendita::restituisciMerce);
	}

	/** Rimette in magazzino la merce e toglie tutte le righe (per riscrivere la vendita). */
	public void svuota() {
		verificaCompletata();
		restituisciMerce();
		new ArrayList<>(righe).forEach(this::rimuoviRiga);
	}

	/** Ordine annullato: la merce torna in magazzino e la vendita non conta nei risultati. */
	public void annulla(LocalDate data, String motivo) {
		verificaCompletata();
		restituisciMerce();
		this.stato = StatoVendita.ANNULLATA;
		this.dataStorno = data;
		this.motivoStorno = motivo;
	}

	/**
	 * Reso del cliente: la merce torna in magazzino; restano a carico i costi non recuperati
	 * (spedizioni, commissioni non rimborsate).
	 */
	public void registraReso(LocalDate data, BigDecimal costiNonRecuperati, String motivo) {
		verificaCompletata();
		restituisciMerce();
		this.stato = StatoVendita.RESA;
		this.dataStorno = data;
		this.motivoStorno = motivo;
		this.costiReso = Importi.importo(costiNonRecuperati);
	}

	private void verificaCompletata() {
		if (stato != StatoVendita.COMPLETATA) {
			throw new IllegalStateException("La vendita è già " + stato.getEtichetta().toLowerCase());
		}
	}

	public boolean isCompletata() {
		return stato == StatoVendita.COMPLETATA;
	}

	public int getTotalePezzi() {
		return righe.stream().mapToInt(RigaVendita::getQuantita).sum();
	}

	public BigDecimal getTotaleCommissioni() {
		return commissioniPiattaforma.add(commissioniPagamento);
	}

	/** Spedizione, imballaggio e altri costi a carico del venditore. */
	public BigDecimal getTotaleCostiVendita() {
		return costoSpedizione.add(costoImballaggio).add(altriCosti);
	}

	/**
	 * Risultato economico: netto meno costo della merce per le vendite completate; per i resi
	 * i soli costi rimasti a carico; zero per gli annullamenti.
	 */
	public BigDecimal getProfitto() {
		return switch (stato) {
			case COMPLETATA -> netto.subtract(Importi.importo(getCostoVenduto()));
			case RESA -> costiReso.negate();
			case ANNULLATA -> BigDecimal.ZERO.setScale(Importi.SCALA_IMPORTO);
		};
	}

	/** Profitto / costo del venduto in percentuale, null se non calcolabile. */
	public BigDecimal getRoi() {
		return isCompletata() ? Importi.percentuale(getProfitto(), getCostoVenduto()) : null;
	}

	/** Profitto / lordo in percentuale, null se non calcolabile. */
	public BigDecimal getMargine() {
		return isCompletata() ? Importi.percentuale(getProfitto(), totaleLordo) : null;
	}

	/** Costo d'acquisto della merce venduta, dalle allocazioni sui lotti. */
	public BigDecimal getCostoVenduto() {
		return righe.stream().map(RigaVendita::getCostoVenduto).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public LocalDate getDataVendita() {
		return dataVendita;
	}

	public void setDataVendita(LocalDate dataVendita) {
		this.dataVendita = dataVendita;
	}

	public Piattaforma getPiattaforma() {
		return piattaforma;
	}

	public void setPiattaforma(Piattaforma piattaforma) {
		this.piattaforma = piattaforma;
	}

	public String getAcquirente() {
		return acquirente;
	}

	public void setAcquirente(String acquirente) {
		this.acquirente = acquirente;
	}

	public String getRiferimentoOrdine() {
		return riferimentoOrdine;
	}

	public void setRiferimentoOrdine(String riferimentoOrdine) {
		this.riferimentoOrdine = riferimentoOrdine;
	}

	public BigDecimal getTotaleLordo() {
		return totaleLordo;
	}

	public BigDecimal getSpedizioneIncassata() {
		return spedizioneIncassata;
	}

	public void setSpedizioneIncassata(BigDecimal spedizioneIncassata) {
		this.spedizioneIncassata = Importi.importo(spedizioneIncassata);
		ricalcolaTotali();
	}

	public BigDecimal getCommissioniPiattaforma() {
		return commissioniPiattaforma;
	}

	public void setCommissioniPiattaforma(BigDecimal commissioniPiattaforma) {
		this.commissioniPiattaforma = Importi.importo(commissioniPiattaforma);
		ricalcolaTotali();
	}

	public BigDecimal getCommissioniPagamento() {
		return commissioniPagamento;
	}

	public void setCommissioniPagamento(BigDecimal commissioniPagamento) {
		this.commissioniPagamento = Importi.importo(commissioniPagamento);
		ricalcolaTotali();
	}

	public BigDecimal getCostoSpedizione() {
		return costoSpedizione;
	}

	public void setCostoSpedizione(BigDecimal costoSpedizione) {
		this.costoSpedizione = Importi.importo(costoSpedizione);
		ricalcolaTotali();
	}

	public BigDecimal getCostoImballaggio() {
		return costoImballaggio;
	}

	public void setCostoImballaggio(BigDecimal costoImballaggio) {
		this.costoImballaggio = Importi.importo(costoImballaggio);
		ricalcolaTotali();
	}

	public BigDecimal getAltriCosti() {
		return altriCosti;
	}

	public void setAltriCosti(BigDecimal altriCosti) {
		this.altriCosti = Importi.importo(altriCosti);
		ricalcolaTotali();
	}

	public BigDecimal getNetto() {
		return netto;
	}

	public StatoVendita getStato() {
		return stato;
	}

	public void setStato(StatoVendita stato) {
		this.stato = stato;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public boolean isCommissioniAutomatiche() {
		return commissioniAutomatiche;
	}

	public void setCommissioniAutomatiche(boolean commissioniAutomatiche) {
		this.commissioniAutomatiche = commissioniAutomatiche;
	}

	public LocalDate getDataStorno() {
		return dataStorno;
	}

	public String getMotivoStorno() {
		return motivoStorno;
	}

	public BigDecimal getCostiReso() {
		return costiReso;
	}

	public List<RigaVendita> getRighe() {
		return Collections.unmodifiableList(righe);
	}

}
