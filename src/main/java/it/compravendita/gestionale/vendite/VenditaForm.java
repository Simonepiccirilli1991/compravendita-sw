package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.catalogo.Articolo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Dati del form vendita. Le righe sono validate da {@link VenditaService#validaRighe}, che
 * ignora quelle vuote e verifica la disponibilità in magazzino.
 */
public class VenditaForm {

	@NotNull(message = "Indica la data della vendita")
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate dataVendita;

	@NotNull(message = "Scegli la piattaforma")
	private Long piattaformaId;

	@Size(max = 255, message = "Massimo 255 caratteri")
	private String acquirente;

	@Size(max = 100, message = "Massimo 100 caratteri")
	private String riferimentoOrdine;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal spedizioneIncassata;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoSpedizione;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoImballaggio;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal altriCosti;

	/** Se vero le commissioni vengono calcolate dalla piattaforma e i valori sotto ignorati. */
	private boolean commissioniAutomatiche = true;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal commissioniPiattaforma;

	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal commissioniPagamento;

	@Size(max = 5000, message = "Massimo 5000 caratteri")
	private String note;

	private List<RigaForm> righe = new ArrayList<>();

	public static VenditaForm nuova() {
		VenditaForm form = new VenditaForm();
		form.dataVendita = LocalDate.now();
		form.righe.add(new RigaForm());
		return form;
	}

	public static VenditaForm da(Vendita vendita, Function<Articolo, String> etichetta) {
		VenditaForm form = new VenditaForm();
		form.dataVendita = vendita.getDataVendita();
		form.piattaformaId = vendita.getPiattaforma().getId();
		form.acquirente = vendita.getAcquirente();
		form.riferimentoOrdine = vendita.getRiferimentoOrdine();
		form.spedizioneIncassata = vendita.getSpedizioneIncassata();
		form.costoSpedizione = vendita.getCostoSpedizione();
		form.costoImballaggio = vendita.getCostoImballaggio();
		form.altriCosti = vendita.getAltriCosti();
		form.commissioniAutomatiche = vendita.isCommissioniAutomatiche();
		form.commissioniPiattaforma = vendita.getCommissioniPiattaforma();
		form.commissioniPagamento = vendita.getCommissioniPagamento();
		form.note = vendita.getNote();
		for (RigaVendita riga : vendita.getRighe()) {
			RigaForm r = new RigaForm();
			r.setArticoloEtichetta(etichetta.apply(riga.getArticolo()));
			r.setArticoloId(riga.getArticolo().getId());
			r.setQuantita(riga.getQuantita());
			r.setPrezzoUnitario(riga.getPrezzoUnitario());
			// se la riga veniva da un solo lotto lo si propone di nuovo
			if (riga.getAllocazioni().size() == 1) {
				r.setLottoId(riga.getAllocazioni().getFirst().getRigaAcquisto().getId());
			}
			form.righe.add(r);
		}
		return form;
	}

	/** Righe con almeno un campo compilato. */
	public List<RigaForm> righeCompilate() {
		return righe.stream().filter(r -> r != null && !r.isVuota()).toList();
	}

	public LocalDate getDataVendita() {
		return dataVendita;
	}

	public void setDataVendita(LocalDate dataVendita) {
		this.dataVendita = dataVendita;
	}

	public Long getPiattaformaId() {
		return piattaformaId;
	}

	public void setPiattaformaId(Long piattaformaId) {
		this.piattaformaId = piattaformaId;
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

	public BigDecimal getSpedizioneIncassata() {
		return spedizioneIncassata;
	}

	public void setSpedizioneIncassata(BigDecimal spedizioneIncassata) {
		this.spedizioneIncassata = spedizioneIncassata;
	}

	public BigDecimal getCostoSpedizione() {
		return costoSpedizione;
	}

	public void setCostoSpedizione(BigDecimal costoSpedizione) {
		this.costoSpedizione = costoSpedizione;
	}

	public BigDecimal getCostoImballaggio() {
		return costoImballaggio;
	}

	public void setCostoImballaggio(BigDecimal costoImballaggio) {
		this.costoImballaggio = costoImballaggio;
	}

	public BigDecimal getAltriCosti() {
		return altriCosti;
	}

	public void setAltriCosti(BigDecimal altriCosti) {
		this.altriCosti = altriCosti;
	}

	public boolean isCommissioniAutomatiche() {
		return commissioniAutomatiche;
	}

	public void setCommissioniAutomatiche(boolean commissioniAutomatiche) {
		this.commissioniAutomatiche = commissioniAutomatiche;
	}

	public BigDecimal getCommissioniPiattaforma() {
		return commissioniPiattaforma;
	}

	public void setCommissioniPiattaforma(BigDecimal commissioniPiattaforma) {
		this.commissioniPiattaforma = commissioniPiattaforma;
	}

	public BigDecimal getCommissioniPagamento() {
		return commissioniPagamento;
	}

	public void setCommissioniPagamento(BigDecimal commissioniPagamento) {
		this.commissioniPagamento = commissioniPagamento;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public List<RigaForm> getRighe() {
		return righe;
	}

	public void setRighe(List<RigaForm> righe) {
		this.righe = righe;
	}

	/** Una riga del form; {@code lottoId} null = allocazione FIFO automatica. */
	public static class RigaForm {

		private Long articoloId;

		private String articoloEtichetta;

		private Integer quantita;

		private BigDecimal prezzoUnitario;

		private Long lottoId;

		public boolean isVuota() {
			return (articoloEtichetta == null || articoloEtichetta.isBlank()) && quantita == null
					&& prezzoUnitario == null;
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

		public Long getLottoId() {
			return lottoId;
		}

		public void setLottoId(Long lottoId) {
			this.lottoId = lottoId;
		}

	}

}
