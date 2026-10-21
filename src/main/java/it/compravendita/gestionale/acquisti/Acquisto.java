package it.compravendita.gestionale.acquisti;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import it.compravendita.gestionale.common.AuditedEntity;
import it.compravendita.gestionale.common.Importi;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Testata di un acquisto: un singolo pezzo o un lotto misto, con i costi accessori
 * da ripartire sulle righe.
 */
@Entity
@Table(name = "acquisto")
public class Acquisto extends AuditedEntity {

	@Column(name = "data_acquisto", nullable = false)
	private LocalDate dataAcquisto;

	private String fornitore;

	@Column(length = 100)
	private String canale;

	@Column(name = "costo_spedizione", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoSpedizione = BigDecimal.ZERO;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal commissioni = BigDecimal.ZERO;

	@Column(name = "altri_costi", nullable = false, precision = 12, scale = 2)
	private BigDecimal altriCosti = BigDecimal.ZERO;

	@Enumerated(EnumType.STRING)
	@Column(name = "metodo_ripartizione", nullable = false, length = 20)
	private MetodoRipartizione metodoRipartizione = MetodoRipartizione.PREZZO;

	private String note;

	@Column(name = "allegato_path", length = 500)
	private String allegatoPath;

	@OneToMany(mappedBy = "acquisto", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id")
	@BatchSize(size = 50)
	private List<RigaAcquisto> righe = new ArrayList<>();

	protected Acquisto() {
	}

	public Acquisto(LocalDate dataAcquisto) {
		this.dataAcquisto = dataAcquisto;
	}

	public void aggiungiRiga(RigaAcquisto riga) {
		riga.setAcquisto(this);
		righe.add(riga);
	}

	public void rimuoviRiga(RigaAcquisto riga) {
		righe.remove(riga);
		riga.setAcquisto(null);
	}

	/**
	 * Distribuisce i costi accessori sulle righe secondo il {@link #getMetodoRipartizione() metodo}
	 * scelto e ricalcola il costo unitario effettivo di ognuna. I centesimi di arrotondamento
	 * vanno alla riga con il peso maggiore, così la somma delle quote coincide con il totale.
	 * Se tutte le righe hanno prezzo zero la ripartizione avviene per quantità.
	 */
	public void ripartisciCostiAccessori() {
		if (righe.isEmpty()) {
			return;
		}
		BigDecimal totaleCosti = getTotaleCostiAccessori();
		boolean perPrezzo = metodoRipartizione == MetodoRipartizione.PREZZO && getTotaleMerce().signum() > 0;
		List<BigDecimal> pesi = righe.stream()
			.map(r -> perPrezzo ? r.getTotalePrezzo() : BigDecimal.valueOf(r.getQuantita()))
			.toList();
		BigDecimal totalePesi = pesi.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal assegnato = BigDecimal.ZERO;
		int indicePesoMaggiore = 0;
		for (int i = 0; i < righe.size(); i++) {
			BigDecimal quota = totaleCosti.multiply(pesi.get(i))
				.divide(totalePesi, Importi.SCALA_IMPORTO, Importi.ARROTONDAMENTO);
			righe.get(i).setQuotaCostiAccessori(quota);
			assegnato = assegnato.add(quota);
			if (pesi.get(i).compareTo(pesi.get(indicePesoMaggiore)) > 0) {
				indicePesoMaggiore = i;
			}
		}
		BigDecimal resto = totaleCosti.subtract(assegnato);
		if (resto.signum() != 0) {
			RigaAcquisto riga = righe.get(indicePesoMaggiore);
			riga.setQuotaCostiAccessori(riga.getQuotaCostiAccessori().add(resto));
		}
	}

	/**
	 * Isola un pezzo disponibile di un lotto in una riga separata dello stesso acquisto,
	 * con lo stesso costo unitario (es. un pezzo da vendere separatamente). Se il lotto ha un solo
	 * pezzo restituisce il lotto stesso.
	 */
	public RigaAcquisto scorporaPezzo(RigaAcquisto lotto) {
		if (!righe.contains(lotto)) {
			throw new IllegalArgumentException("Il lotto non appartiene a questo acquisto");
		}
		if (lotto.getQuantitaResidua() < 1) {
			throw new IllegalStateException("Il lotto non ha pezzi disponibili");
		}
		if (lotto.getQuantita() == 1) {
			return lotto;
		}
		RigaAcquisto pezzo = lotto.separaPezzo();
		aggiungiRiga(pezzo);
		return pezzo;
	}

	/** Spedizione + commissioni + altri costi. */
	public BigDecimal getTotaleCostiAccessori() {
		return Importi.nonNullo(costoSpedizione).add(Importi.nonNullo(commissioni)).add(Importi.nonNullo(altriCosti));
	}

	/** Somma di prezzo unitario x quantità delle righe, senza costi accessori. */
	public BigDecimal getTotaleMerce() {
		return righe.stream().map(RigaAcquisto::getTotalePrezzo).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public int getTotalePezzi() {
		return righe.stream().mapToInt(RigaAcquisto::getQuantita).sum();
	}

	public BigDecimal getTotale() {
		return getTotaleMerce().add(getTotaleCostiAccessori());
	}

	public LocalDate getDataAcquisto() {
		return dataAcquisto;
	}

	public void setDataAcquisto(LocalDate dataAcquisto) {
		this.dataAcquisto = dataAcquisto;
	}

	public String getFornitore() {
		return fornitore;
	}

	public void setFornitore(String fornitore) {
		this.fornitore = fornitore;
	}

	public String getCanale() {
		return canale;
	}

	public void setCanale(String canale) {
		this.canale = canale;
	}

	public BigDecimal getCostoSpedizione() {
		return costoSpedizione;
	}

	public void setCostoSpedizione(BigDecimal costoSpedizione) {
		this.costoSpedizione = Importi.nonNullo(costoSpedizione);
	}

	public BigDecimal getCommissioni() {
		return commissioni;
	}

	public void setCommissioni(BigDecimal commissioni) {
		this.commissioni = Importi.nonNullo(commissioni);
	}

	public BigDecimal getAltriCosti() {
		return altriCosti;
	}

	public void setAltriCosti(BigDecimal altriCosti) {
		this.altriCosti = Importi.nonNullo(altriCosti);
	}

	public MetodoRipartizione getMetodoRipartizione() {
		return metodoRipartizione;
	}

	public void setMetodoRipartizione(MetodoRipartizione metodoRipartizione) {
		this.metodoRipartizione = metodoRipartizione;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public String getAllegatoPath() {
		return allegatoPath;
	}

	public void setAllegatoPath(String allegatoPath) {
		this.allegatoPath = allegatoPath;
	}

	public List<RigaAcquisto> getRighe() {
		return Collections.unmodifiableList(righe);
	}

}
