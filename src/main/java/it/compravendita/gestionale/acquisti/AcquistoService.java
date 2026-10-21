package it.compravendita.gestionale.acquisti;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import it.compravendita.gestionale.acquisti.AcquistoForm.RigaForm;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.common.FileStorage;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;
import it.compravendita.gestionale.magazzino.StatoOggetto;

@Service
@Transactional(readOnly = true)
public class AcquistoService {

	static final String CARTELLA_ALLEGATI = "acquisti";

	private final AcquistoRepository acquistoRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final ArticoloRepository articoloRepository;

	private final FileStorage fileStorage;

	public AcquistoService(AcquistoRepository acquistoRepository, RigaAcquistoRepository rigaAcquistoRepository,
			ArticoloRepository articoloRepository, FileStorage fileStorage) {
		this.acquistoRepository = acquistoRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.articoloRepository = articoloRepository;
		this.fileStorage = fileStorage;
	}

	public Page<Acquisto> cerca(FiltroAcquisti filtro, Pageable pageable) {
		Page<Acquisto> pagina = acquistoRepository.findAll(filtro.toSpecification(), pageable);
		// righe e articoli servono alla lista per totali e nomi (caricati a blocchi grazie a @BatchSize)
		pagina.forEach(a -> a.getRighe().forEach(r -> r.getArticolo().getNome()));
		return pagina;
	}

	public Acquisto trova(Long id) {
		return acquistoRepository.findConRigheById(id).orElseThrow(() -> new RisorsaNonTrovataException("Acquisto", id));
	}

	/**
	 * Righe che hanno avuto movimenti (vendite, rettifiche, spese collegate):
	 * non si possono modificare né eliminare.
	 */
	public Set<Long> righeBloccate(Acquisto acquisto) {
		if (acquisto.isNuovo()) {
			return Set.of();
		}
		Set<Long> bloccate = new HashSet<>(rigaAcquistoRepository.findIdConCollegamenti(acquisto.getId()));
		acquisto.getRighe().stream().filter(RigaAcquisto::isMovimentato).forEach(r -> bloccate.add(r.getId()));
		return bloccate;
	}

	/**
	 * Valida le righe compilate del form (quelle completamente vuote sono ignorate) e ne risolve
	 * l'articolo a partire dal testo scelto nell'elenco. Le righe bloccate non sono modificabili
	 * e quindi non vengono validate.
	 */
	public void validaRighe(AcquistoForm form, Set<Long> bloccate, Errors errors) {
		List<RigaForm> righe = form.getRighe();
		boolean almenoUna = false;
		for (int i = 0; i < righe.size(); i++) {
			RigaForm riga = righe.get(i);
			if (riga == null || riga.isVuota()) {
				continue;
			}
			almenoUna = true;
			if (riga.getId() != null && bloccate.contains(riga.getId())) {
				continue;
			}
			String campo = "righe[" + i + "].";
			Long articoloId = ElencoArticoli.idDaEtichetta(riga.getArticoloEtichetta());
			riga.setArticoloId(articoloId);
			if (articoloId == null || !articoloRepository.existsById(articoloId)) {
				errors.rejectValue(campo + "articoloEtichetta", "articolo", "Scegli un articolo dall'elenco");
			}
			if (riga.getQuantita() == null || riga.getQuantita() < 1) {
				errors.rejectValue(campo + "quantita", "quantita", "Almeno 1");
			}
			if (riga.getPrezzoUnitario() == null || riga.getPrezzoUnitario().signum() < 0) {
				errors.rejectValue(campo + "prezzoUnitario", "prezzo", "Prezzo non valido");
			}
		}
		if (!almenoUna) {
			errors.reject("righe", "Aggiungi almeno un articolo");
		}
	}

	/**
	 * Crea (id null) o aggiorna un acquisto e ripartisce i costi accessori. Le righe bloccate
	 * restano invariate; quelle non più presenti nel form vengono eliminate.
	 *
	 * @param allegato nuova ricevuta, opzionale
	 */
	@Transactional
	public Acquisto salva(Long id, AcquistoForm form, MultipartFile allegato) {
		Acquisto acquisto = id == null ? new Acquisto(form.getDataAcquisto()) : trova(id);
		Set<Long> bloccate = righeBloccate(acquisto);

		acquisto.setDataAcquisto(form.getDataAcquisto());
		acquisto.setFornitore(vuotoComeNull(form.getFornitore()));
		acquisto.setCanale(vuotoComeNull(form.getCanale()));
		acquisto.setCostoSpedizione(Importi.importo(form.getCostoSpedizione()));
		acquisto.setCommissioni(Importi.importo(form.getCommissioni()));
		acquisto.setAltriCosti(Importi.importo(form.getAltriCosti()));
		acquisto.setMetodoRipartizione(form.getMetodoRipartizione());
		acquisto.setNote(vuotoComeNull(form.getNote()));

		Map<Long, RigaAcquisto> esistenti = new HashMap<>();
		acquisto.getRighe().forEach(r -> esistenti.put(r.getId(), r));
		Set<Long> mantenute = new HashSet<>();

		for (RigaForm dati : form.righeCompilate()) {
			if (dati.getId() != null) {
				RigaAcquisto riga = esistenti.get(dati.getId());
				if (riga == null) {
					throw new RisorsaNonTrovataException("Riga d'acquisto", dati.getId());
				}
				mantenute.add(riga.getId());
				if (!bloccate.contains(riga.getId())) {
					riga.modifica(articolo(dati), dati.getQuantita(), dati.getPrezzoUnitario());
					riga.setStato(statoIniziale(dati));
				}
			}
			else {
				RigaAcquisto riga = new RigaAcquisto(articolo(dati), dati.getQuantita(), dati.getPrezzoUnitario());
				riga.setStato(statoIniziale(dati));
				acquisto.aggiungiRiga(riga);
			}
		}
		for (RigaAcquisto riga : new ArrayList<>(acquisto.getRighe())) {
			if (riga.getId() != null && !mantenute.contains(riga.getId())) {
				if (bloccate.contains(riga.getId())) {
					throw new OperazioneNonConsentitaException("La riga \"" + riga.getArticolo().getNome()
							+ "\" ha movimenti di magazzino e non può essere rimossa");
				}
				acquisto.rimuoviRiga(riga);
			}
		}
		acquisto.ripartisciCostiAccessori();

		String allegatoDaEliminare = null;
		if (allegato != null && !allegato.isEmpty()) {
			allegatoDaEliminare = acquisto.getAllegatoPath();
			acquisto.setAllegatoPath(fileStorage.salvaAllegato(allegato, CARTELLA_ALLEGATI));
		}
		else if (form.isRimuoviAllegato()) {
			allegatoDaEliminare = acquisto.getAllegatoPath();
			acquisto.setAllegatoPath(null);
		}

		Acquisto salvato = acquistoRepository.save(acquisto);
		fileStorage.elimina(allegatoDaEliminare);
		return salvato;
	}

	@Transactional
	public void elimina(Long id) {
		Acquisto acquisto = trova(id);
		if (!righeBloccate(acquisto).isEmpty()) {
			throw new OperazioneNonConsentitaException(
					"L'acquisto ha merce già venduta o rettificata e non può essere eliminato");
		}
		acquistoRepository.delete(acquisto);
		fileStorage.elimina(acquisto.getAllegatoPath());
	}

	/**
	 * Calcola, senza salvare, la ripartizione dei costi accessori per le righe del form.
	 * Le righe incomplete hanno quota e costo unitario null.
	 */
	public Anteprima anteprima(AcquistoForm form) {
		Acquisto prova = new Acquisto(form.getDataAcquisto());
		prova.setCostoSpedizione(form.getCostoSpedizione());
		prova.setCommissioni(form.getCommissioni());
		prova.setAltriCosti(form.getAltriCosti());
		prova.setMetodoRipartizione(
				form.getMetodoRipartizione() != null ? form.getMetodoRipartizione() : MetodoRipartizione.PREZZO);

		List<RigaForm> compilate = form.righeCompilate();
		List<RigaAcquisto> calcolate = new ArrayList<>();
		for (RigaForm dati : compilate) {
			boolean completa = dati.getQuantita() != null && dati.getQuantita() > 0 && dati.getPrezzoUnitario() != null
					&& dati.getPrezzoUnitario().signum() >= 0;
			RigaAcquisto riga = completa ? new RigaAcquisto(null, dati.getQuantita(), dati.getPrezzoUnitario()) : null;
			if (riga != null) {
				prova.aggiungiRiga(riga);
			}
			calcolate.add(riga);
		}
		prova.ripartisciCostiAccessori();

		List<Anteprima.Riga> righe = new ArrayList<>();
		for (int i = 0; i < compilate.size(); i++) {
			RigaAcquisto riga = calcolate.get(i);
			righe.add(new Anteprima.Riga(etichettaBreve(compilate.get(i).getArticoloEtichetta()),
					compilate.get(i).getQuantita(), riga == null ? null : riga.getTotalePrezzo(),
					riga == null ? null : riga.getQuotaCostiAccessori(),
					riga == null ? null : riga.getCostoUnitarioEffettivo()));
		}
		return new Anteprima(righe, prova.getTotalePezzi(), prova.getTotaleMerce(), prova.getTotaleCostiAccessori(),
				prova.getTotale());
	}

	private Articolo articolo(RigaForm dati) {
		Long articoloId = dati.getArticoloId() != null ? dati.getArticoloId()
				: ElencoArticoli.idDaEtichetta(dati.getArticoloEtichetta());
		if (articoloId == null) {
			throw new OperazioneNonConsentitaException("Articolo non indicato");
		}
		return articoloRepository.findById(articoloId)
			.orElseThrow(() -> new RisorsaNonTrovataException("Articolo", articoloId));
	}

	private static StatoOggetto statoIniziale(RigaForm dati) {
		return dati.getStato() != null && StatoOggetto.iniziali().contains(dati.getStato()) ? dati.getStato()
				: StatoOggetto.IN_STOCK;
	}

	/** Etichetta senza il suffisso "#id". */
	private static String etichettaBreve(String etichetta) {
		if (etichetta == null || etichetta.isBlank()) {
			return "—";
		}
		return etichetta.replaceFirst("\\s*#\\d+\\s*$", "");
	}

	private static String vuotoComeNull(String valore) {
		return valore == null || valore.isBlank() ? null : valore.trim();
	}

	/** Ripartizione calcolata per l'anteprima del form. */
	public record Anteprima(List<Riga> righe, int totalePezzi, BigDecimal totaleMerce, BigDecimal totaleCosti,
			BigDecimal totale) {

		public record Riga(String articolo, Integer quantita, BigDecimal totalePrezzo, BigDecimal quota,
				BigDecimal costoUnitario) {
		}

	}

}
