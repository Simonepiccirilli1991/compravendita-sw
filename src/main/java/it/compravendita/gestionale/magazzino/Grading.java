package it.compravendita.gestionale.magazzino;

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
 * Invio di una carta a un ente di grading. La carta è rappresentata da un lotto di un pezzo
 * ({@link #getRigaAcquisto()}): al rientro il lotto passa all'articolo gradato e il costo del
 * grading si somma al suo costo.
 */
@Entity
@Table(name = "grading")
public class Grading extends AuditedEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "riga_acquisto_id", nullable = false)
	private RigaAcquisto rigaAcquisto;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "articolo_originale_id", nullable = false)
	private Articolo articoloOriginale;

	@Column(nullable = false, length = 30)
	private String ente;

	@Column(length = 100)
	private String servizio;

	@Column(name = "numero_ordine", length = 100)
	private String numeroOrdine;

	@Column(name = "data_invio", nullable = false)
	private LocalDate dataInvio;

	@Column(name = "data_rientro")
	private LocalDate dataRientro;

	/** Costo previsto all'invio, definitivo al rientro. */
	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal costo = BigDecimal.ZERO;

	@Column(precision = 4, scale = 1)
	private BigDecimal voto;

	@Column(name = "numero_certificato", length = 100)
	private String numeroCertificato;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private StatoGrading stato = StatoGrading.INVIATO;

	private String note;

	protected Grading() {
	}

	/** @param carta lotto di un solo pezzo disponibile, già scorporato */
	public Grading(RigaAcquisto carta, String ente, LocalDate dataInvio) {
		if (carta.getQuantita() != 1 || carta.getQuantitaResidua() != 1) {
			throw new IllegalArgumentException("Il grading riguarda un lotto di un solo pezzo disponibile");
		}
		this.rigaAcquisto = carta;
		this.articoloOriginale = carta.getArticolo();
		this.ente = ente;
		this.dataInvio = dataInvio;
		carta.setStato(StatoOggetto.IN_GRADING);
	}

	/** Rientro della carta: il lotto passa all'articolo gradato e ne assorbe il costo. */
	public void rientra(Articolo articoloGradato, LocalDate data, BigDecimal voto, String numeroCertificato,
			BigDecimal costoFinale) {
		verificaInviato();
		this.dataRientro = data;
		this.voto = voto;
		this.numeroCertificato = numeroCertificato;
		this.costo = Importi.importo(costoFinale);
		this.stato = StatoGrading.RIENTRATO;
		rigaAcquisto.setArticolo(articoloGradato);
		rigaAcquisto.aggiungiCostoAggiuntivo(this.costo);
		rigaAcquisto.setStato(StatoOggetto.IN_STOCK);
	}

	/** La carta torna disponibile senza voto e senza costi aggiuntivi. */
	public void annulla() {
		verificaInviato();
		this.stato = StatoGrading.ANNULLATO;
		rigaAcquisto.setStato(StatoOggetto.IN_STOCK);
	}

	public boolean isInviato() {
		return stato == StatoGrading.INVIATO;
	}

	private void verificaInviato() {
		if (!isInviato()) {
			throw new IllegalStateException("Il grading è già " + stato.getEtichetta().toLowerCase());
		}
	}

	public RigaAcquisto getRigaAcquisto() {
		return rigaAcquisto;
	}

	public Articolo getArticoloOriginale() {
		return articoloOriginale;
	}

	public String getEnte() {
		return ente;
	}

	public String getServizio() {
		return servizio;
	}

	public void setServizio(String servizio) {
		this.servizio = servizio;
	}

	public String getNumeroOrdine() {
		return numeroOrdine;
	}

	public void setNumeroOrdine(String numeroOrdine) {
		this.numeroOrdine = numeroOrdine;
	}

	public LocalDate getDataInvio() {
		return dataInvio;
	}

	public LocalDate getDataRientro() {
		return dataRientro;
	}

	public BigDecimal getCosto() {
		return costo;
	}

	public void setCosto(BigDecimal costo) {
		this.costo = Importi.importo(costo);
	}

	public BigDecimal getVoto() {
		return voto;
	}

	public String getNumeroCertificato() {
		return numeroCertificato;
	}

	public StatoGrading getStato() {
		return stato;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
