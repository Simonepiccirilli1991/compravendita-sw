package it.compravendita.gestionale.prezzi;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaService;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.Periodo;
import it.compravendita.gestionale.spese.Spesa;
import it.compravendita.gestionale.spese.SpesaRepository;

@Service
@Transactional(readOnly = true)
public class PrezziService {

	private final ParametriPrezzoRepository parametriRepository;

	private final PiattaformaService piattaformaService;

	private final SpesaRepository spesaRepository;

	private final CalcoloEconomicoService calcoloEconomicoService;

	public PrezziService(ParametriPrezzoRepository parametriRepository, PiattaformaService piattaformaService,
			SpesaRepository spesaRepository, CalcoloEconomicoService calcoloEconomicoService) {
		this.parametriRepository = parametriRepository;
		this.piattaformaService = piattaformaService;
		this.spesaRepository = spesaRepository;
		this.calcoloEconomicoService = calcoloEconomicoService;
	}

	public ParametriPrezzo parametri() {
		return parametriRepository.findById(ParametriPrezzo.ID)
			.orElseThrow(() -> new IllegalStateException("Parametri dei prezzi mancanti nel database"));
	}

	/** Calcolo dei prezzi suggeriti con i parametri salvati. */
	public CalcoloPrezzi calcolo() {
		return CalcoloPrezzi.da(parametri());
	}

	/** Calcolo con i valori di un form non ancora salvato (anteprima nella pagina dei parametri). */
	public CalcoloPrezzi calcolo(ParametriPrezzoForm form) {
		return form.calcolo(piattaforma(form.getPiattaformaId()));
	}

	@Transactional
	public ParametriPrezzo salva(ParametriPrezzoForm form) {
		ParametriPrezzo p = parametri();
		p.setCoefficienteRedditivita(form.getCoefficienteRedditivita());
		p.setAliquotaImposta(form.getAliquotaImposta());
		p.setAliquotaContributi(form.getAliquotaContributi());
		p.setRiduzioneContributi(form.getRiduzioneContributi());
		p.setMinimaleInps(Importi.importo(form.getMinimaleInps()));
		p.setContributoMaternita(Importi.importo(form.getContributoMaternita()));
		p.setAltriCostiFissi(Importi.importo(form.getAltriCostiFissi()));
		p.setRicaviAnnuiPrevisti(Importi.importo(form.getRicaviAnnuiPrevisti()));
		p.setMargineDesiderato(form.getMargineDesiderato());
		p.setCostoImballaggio(Importi.importo(form.getCostoImballaggio()));
		p.setCostoSpedizione(Importi.importo(form.getCostoSpedizione()));
		p.setPiattaforma(piattaforma(form.getPiattaformaId()));
		return p;
	}

	/**
	 * Spese generali (non collegate a un articolo) registrate negli ultimi 12 mesi: un riferimento
	 * per stimare i costi fissi annui.
	 */
	public BigDecimal speseGeneraliUltimoAnno() {
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		return Importi.importo(spesaRepository.findByDataSpesaBetweenOrderByDataSpesaDesc(oggi.minusYears(1), oggi)
			.stream()
			.filter(s -> s.getArticolo() == null && s.getRigaAcquisto() == null)
			.map(Spesa::getImporto)
			.reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	/** Incassi delle vendite degli ultimi 12 mesi: un riferimento per stimare i ricavi annui. */
	public BigDecimal ricaviUltimoAnno() {
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		return Importi.importo(calcoloEconomicoService.totale(new Periodo(oggi.minusYears(1), oggi)).lordo());
	}

	private Piattaforma piattaforma(Long id) {
		return id == null ? null : piattaformaService.trova(id);
	}

}
