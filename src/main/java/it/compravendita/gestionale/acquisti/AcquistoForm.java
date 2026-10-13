package it.compravendita.gestionale.acquisti;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Dati del form acquisto: testata e righe. Le righe sono validate a parte da
 * {@link AcquistoService#validaRighe} perché quelle completamente vuote vengono ignorate.
 */
public class AcquistoForm {

	@NotNull(message = "Indica la data dell'acquisto")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dataAcquisto;

	@Size(max = 255, message = "Massimo 255 caratteri")
	private String fornitore;

	@Size(max = 100, message = "Massimo 100 caratteri")
	private String canale;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoSpedizione;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal commissioni;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal altriCosti;

	@NotNull(message = "Scegli come ripartire i costi")
	private MetodoRipartizione metodoRipartizione = MetodoRipartizione.PREZZO;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	private boolean rimuoviAllegato;

	private List<RigaForm> righe = new ArrayList<>();

	public static AcquistoForm nuovo() {
		AcquistoForm form = new AcquistoForm();
		form.setDataAcquisto(LocalDate.now());
		form.getRighe().add(new RigaForm());
		return form;
	}

	/**
	 * @param etichetta testo con cui mostrare l'articolo nel campo di ricerca
	 */
	public static AcquistoForm da(Acquisto acquisto, Set<Long> righeBloccate, Function<Articolo, String> etichetta) {
		AcquistoForm form = new AcquistoForm();
		form.dataAcquisto = acquisto.getDataAcquisto();
		form.fornitore = acquisto.getFornitore();
		form.canale = acquisto.getCanale();
		form.costoSpedizione = acquisto.getCostoSpedizione();
		form.commissioni = acquisto.getCommissioni();
		form.altriCosti = acquisto.getAltriCosti();
		form.metodoRipartizione = acquisto.getMetodoRipartizione();
		form.note = acquisto.getNote();
		for (RigaAcquisto riga : acquisto.getRighe()) {
			RigaForm r = new RigaForm();
			r.setId(riga.getId());
			r.setArticoloId(riga.getArticolo().getId());
			r.setArticoloEtichetta(etichetta.apply(riga.getArticolo()));
			r.setQuantita(riga.getQuantita());
			r.setPrezzoUnitario(riga.getPrezzoUnitario());
			r.setStato(riga.getStato());
			r.setBloccata(righeBloccate.contains(riga.getId()));
			form.righe.add(r);
		}
		return form;
	}

	/** Righe con almeno un campo compilato. */
	public List<RigaForm> righeCompilate() {
		return righe.stream().filter(r -> r != null && !r.isVuota()).toList();
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
		this.costoSpedizione = costoSpedizione;
	}

	public BigDecimal getCommissioni() {
		return commissioni;
	}

	public void setCommissioni(BigDecimal commissioni) {
		this.commissioni = commissioni;
	}

	public BigDecimal getAltriCosti() {
		return altriCosti;
	}

	public void setAltriCosti(BigDecimal altriCosti) {
		this.altriCosti = altriCosti;
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

	public boolean isRimuoviAllegato() {
		return rimuoviAllegato;
	}

	public void setRimuoviAllegato(boolean rimuoviAllegato) {
		this.rimuoviAllegato = rimuoviAllegato;
	}

	public List<RigaForm> getRighe() {
		return righe;
	}

	public void setRighe(List<RigaForm> righe) {
		this.righe = righe;
	}

	/** Una riga del form. {@code id} è valorizzato per le righe già salvate. */
	public static class RigaForm {

		private Long id;

		private Long articoloId;

		/** Testo mostrato nel campo di ricerca articolo. */
		private String articoloEtichetta;

		private Integer quantita;

		private BigDecimal prezzoUnitario;

		private StatoOggetto stato = StatoOggetto.IN_STOCK;

		/** Riga con movimenti: mostrata in sola lettura. */
		private boolean bloccata;

		public boolean isVuota() {
			return id == null && articoloId == null && (articoloEtichetta == null || articoloEtichetta.isBlank())
					&& quantita == null && prezzoUnitario == null;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getArticoloId() {
			return articoloId;
		}

		public void setArticoloId(Long articoloId) {
			this.articoloId = articoloId;
		}

		public String getArticoloEtichetta() {
			return articoloEtichetta;
		}

		public void setArticoloEtichetta(String articoloEtichetta) {
			this.articoloEtichetta = articoloEtichetta;
		}

		public Integer getQuantita() {
			return quantita;
		}

		public void setQuantita(Integer quantita) {
			this.quantita = quantita;
		}

		public BigDecimal getPrezzoUnitario() {
			return prezzoUnitario;
		}

		public void setPrezzoUnitario(BigDecimal prezzoUnitario) {
			this.prezzoUnitario = prezzoUnitario;
		}

		public StatoOggetto getStato() {
			return stato;
		}

		public void setStato(StatoOggetto stato) {
			this.stato = stato;
		}

		public boolean isBloccata() {
			return bloccata;
		}

		public void setBloccata(boolean bloccata) {
			this.bloccata = bloccata;
		}

	}

}
