package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;

import it.compravendita.gestionale.piattaforme.Piattaforma;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class ParametriPrezzoForm implements ValoriPrezzo {

	@NotNull(message = "Indica il coefficiente")
	@PositiveOrZero(message = "Non può essere negativo")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal coefficienteRedditivita;

	@NotNull(message = "Indica l'aliquota")
	@PositiveOrZero(message = "Non può essere negativa")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal aliquotaImposta;

	@NotNull(message = "Indica l'aliquota (0 se non dovuti)")
	@PositiveOrZero(message = "Non può essere negativa")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal aliquotaContributi;

	@NotNull(message = "Indica la riduzione (0 se non prevista)")
	@PositiveOrZero(message = "Non può essere negativa")
	@DecimalMax(value = "100", message = "Massimo 100%")
	private BigDecimal riduzioneContributi;

	@NotNull(message = "Indica il minimale (0 se non previsto)")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal minimaleInps;

	@NotNull(message = "Indica l'importo (0 se non dovuto)")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal contributoMaternita;

	@NotNull(message = "Indica l'importo (0 se non previsti)")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal altriCostiFissi;

	@NotNull(message = "Indica quanto pensi di incassare in un anno")
	@DecimalMin(value = "1", message = "Almeno 1 €")
	private BigDecimal ricaviAnnuiPrevisti;

	@NotNull(message = "Indica il margine (0 per nessun margine)")
	@PositiveOrZero(message = "Non può essere negativo")
	@DecimalMax(value = "1000", message = "Massimo 1000%")
	private BigDecimal margineDesiderato;

	@NotNull(message = "Indica l'importo (0 se non previsto)")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoImballaggio;

	@NotNull(message = "Indica l'importo (0 se la paga l'acquirente)")
	@PositiveOrZero(message = "Non può essere negativo")
	private BigDecimal costoSpedizione;

	private Long piattaformaId;

	public static ParametriPrezzoForm da(ParametriPrezzo p) {
		ParametriPrezzoForm form = new ParametriPrezzoForm();
		form.coefficienteRedditivita = p.getCoefficienteRedditivita();
		form.aliquotaImposta = p.getAliquotaImposta();
		form.aliquotaContributi = p.getAliquotaContributi();
		form.riduzioneContributi = p.getRiduzioneContributi();
		form.minimaleInps = p.getMinimaleInps();
		form.contributoMaternita = p.getContributoMaternita();
		form.altriCostiFissi = p.getAltriCostiFissi();
		form.ricaviAnnuiPrevisti = p.getRicaviAnnuiPrevisti();
		form.margineDesiderato = p.getMargineDesiderato();
		form.costoImballaggio = p.getCostoImballaggio();
		form.costoSpedizione = p.getCostoSpedizione();
		form.piattaformaId = p.getPiattaforma() == null ? null : p.getPiattaforma().getId();
		return form;
	}

	/** Calcolo con i valori del form (anche non salvati), per l'anteprima. */
	public CalcoloPrezzi calcolo(Piattaforma piattaforma) {
		return CalcoloPrezzi.di(this, piattaforma);
	}

	@Override
	public BigDecimal getCoefficienteRedditivita() {
		return coefficienteRedditivita;
	}

	public void setCoefficienteRedditivita(BigDecimal coefficienteRedditivita) {
		this.coefficienteRedditivita = coefficienteRedditivita;
	}

	@Override
	public BigDecimal getAliquotaImposta() {
		return aliquotaImposta;
	}

	public void setAliquotaImposta(BigDecimal aliquotaImposta) {
		this.aliquotaImposta = aliquotaImposta;
	}

	@Override
	public BigDecimal getAliquotaContributi() {
		return aliquotaContributi;
	}

	public void setAliquotaContributi(BigDecimal aliquotaContributi) {
		this.aliquotaContributi = aliquotaContributi;
	}

	@Override
	public BigDecimal getRiduzioneContributi() {
		return riduzioneContributi;
	}

	public void setRiduzioneContributi(BigDecimal riduzioneContributi) {
		this.riduzioneContributi = riduzioneContributi;
	}

	@Override
	public BigDecimal getMinimaleInps() {
		return minimaleInps;
	}

	public void setMinimaleInps(BigDecimal minimaleInps) {
		this.minimaleInps = minimaleInps;
	}

	@Override
	public BigDecimal getContributoMaternita() {
		return contributoMaternita;
	}

	public void setContributoMaternita(BigDecimal contributoMaternita) {
		this.contributoMaternita = contributoMaternita;
	}

	@Override
	public BigDecimal getAltriCostiFissi() {
		return altriCostiFissi;
	}

	public void setAltriCostiFissi(BigDecimal altriCostiFissi) {
		this.altriCostiFissi = altriCostiFissi;
	}

	@Override
	public BigDecimal getRicaviAnnuiPrevisti() {
		return ricaviAnnuiPrevisti;
	}

	public void setRicaviAnnuiPrevisti(BigDecimal ricaviAnnuiPrevisti) {
		this.ricaviAnnuiPrevisti = ricaviAnnuiPrevisti;
	}

	@Override
	public BigDecimal getMargineDesiderato() {
		return margineDesiderato;
	}

	public void setMargineDesiderato(BigDecimal margineDesiderato) {
		this.margineDesiderato = margineDesiderato;
	}

	@Override
	public BigDecimal getCostoImballaggio() {
		return costoImballaggio;
	}

	public void setCostoImballaggio(BigDecimal costoImballaggio) {
		this.costoImballaggio = costoImballaggio;
	}

	@Override
	public BigDecimal getCostoSpedizione() {
		return costoSpedizione;
	}

	public void setCostoSpedizione(BigDecimal costoSpedizione) {
		this.costoSpedizione = costoSpedizione;
	}

	public Long getPiattaformaId() {
		return piattaformaId;
	}

	public void setPiattaformaId(Long piattaformaId) {
		this.piattaformaId = piattaformaId;
	}

}
