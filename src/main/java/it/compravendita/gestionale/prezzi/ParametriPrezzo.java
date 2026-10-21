package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import it.compravendita.gestionale.piattaforme.Piattaforma;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Parametri dell'attività usati per suggerire i prezzi di vendita (una sola riga, id 1): regime
 * forfettario (coefficiente di redditività, imposta sostitutiva), contributi INPS dei commercianti
 * con l'eventuale riduzione del regime agevolato, costi di vendita, costi fissi e margine desiderato.
 */
@Entity
@Table(name = "parametri_prezzo")
public class ParametriPrezzo implements ValoriPrezzo {

	public static final Integer ID = 1;

	@Id
	private Integer id = ID;

	@Column(name = "coefficiente_redditivita", nullable = false, precision = 5, scale = 2)
	private BigDecimal coefficienteRedditivita;

	@Column(name = "aliquota_imposta", nullable = false, precision = 5, scale = 2)
	private BigDecimal aliquotaImposta;

	@Column(name = "aliquota_contributi", nullable = false, precision = 5, scale = 2)
	private BigDecimal aliquotaContributi;

	@Column(name = "riduzione_contributi", nullable = false, precision = 5, scale = 2)
	private BigDecimal riduzioneContributi;

	@Column(name = "minimale_inps", nullable = false, precision = 12, scale = 2)
	private BigDecimal minimaleInps;

	@Column(name = "contributo_maternita", nullable = false, precision = 12, scale = 2)
	private BigDecimal contributoMaternita;

	@Column(name = "altri_costi_fissi", nullable = false, precision = 12, scale = 2)
	private BigDecimal altriCostiFissi;

	@Column(name = "ricavi_annui_previsti", nullable = false, precision = 12, scale = 2)
	private BigDecimal ricaviAnnuiPrevisti;

	@Column(name = "margine_desiderato", nullable = false, precision = 6, scale = 2)
	private BigDecimal margineDesiderato;

	@Column(name = "costo_imballaggio", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoImballaggio;

	@Column(name = "costo_spedizione", nullable = false, precision = 12, scale = 2)
	private BigDecimal costoSpedizione;

	/** Piattaforma su cui si vende di solito: le sue commissioni entrano nel prezzo. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "piattaforma_id")
	private Piattaforma piattaforma;

	@UpdateTimestamp
	@Column(name = "aggiornato_il", nullable = false)
	private Instant aggiornatoIl;

	protected ParametriPrezzo() {
	}

	public Integer getId() {
		return id;
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

	public Piattaforma getPiattaforma() {
		return piattaforma;
	}

	public void setPiattaforma(Piattaforma piattaforma) {
		this.piattaforma = piattaforma;
	}

	public Instant getAggiornatoIl() {
		return aggiornatoIl;
	}

}
