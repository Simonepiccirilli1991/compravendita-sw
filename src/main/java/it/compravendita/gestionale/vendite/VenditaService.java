package it.compravendita.gestionale.vendite;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import it.compravendita.gestionale.piattaforme.Commissioni;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.vendite.VenditaForm.RigaForm;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Registrazione delle vendite con calcolo delle commissioni e allocazione della merce sui
 * lotti d'acquisto (FIFO, oppure da un lotto scelto), annullamenti e resi.
 */
@Service
@Transactional(readOnly = true)
public class VenditaService {

	private final VenditaRepository venditaRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final ArticoloRepository articoloRepository;

	private final PiattaformaRepository piattaformaRepository;

	private final EntityManager em;

	public VenditaService(VenditaRepository venditaRepository, RigaAcquistoRepository rigaAcquistoRepository,
			ArticoloRepository articoloRepository, PiattaformaRepository piattaformaRepository, EntityManager em) {
		this.venditaRepository = venditaRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.articoloRepository = articoloRepository;
		this.piattaformaRepository = piattaformaRepository;
		this.em = em;
	}

	public Page<Vendita> cerca(FiltroVendite filtro, Pageable pageable) {
		Page<Vendita> pagina = venditaRepository.findAll(filtro.toSpecification(), pageable);
		pagina.forEach(VenditaService::inizializza);
		return pagina;
	}

	/** Vendita con righe, articoli e allocazioni caricate. */
	public Vendita trova(Long id) {
		Vendita vendita = venditaRepository.findConPiattaformaById(id)
			.orElseThrow(() -> new RisorsaNonTrovataException("Vendita", id));
		inizializza(vendita);
		vendita.getRighe().forEach(r -> r.getAllocazioni()
			.forEach(a -> a.getRigaAcquisto().getAcquisto().getDataAcquisto()));
		return vendita;
	}

	public List<RigaVendita> perArticolo(Long articoloId) {
		List<RigaVendita> righe = venditaRepository.findRigheByArticolo(articoloId);
		righe.forEach(r -> r.getAllocazioni().size());
		return righe;
	}

	/** Lotti da cui si può vendere l'articolo, in ordine FIFO. */
	public List<RigaAcquisto> lottiVendibili(Long articoloId) {
		List<RigaAcquisto> lotti = rigaAcquistoRepository.findDisponibiliFifo(articoloId);
		lotti.forEach(l -> l.getAcquisto().getDataAcquisto());
		return lotti;
	}

	/** Totali delle vendite filtrate: solo le completate contano per fatturato e profitto. */
	public Totali totali(FiltroVendite filtro) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		Specification<Vendita> completate = filtro.toSpecification()
			.and((root, query, b) -> b.equal(root.get("stato"), StatoVendita.COMPLETATA));

		CriteriaQuery<Object[]> q = cb.createQuery(Object[].class);
		Root<Vendita> v = q.from(Vendita.class);
		q.multiselect(cb.count(v), cb.coalesce(cb.sum(v.<BigDecimal>get("totaleLordo")), BigDecimal.ZERO),
				cb.coalesce(cb.sum(v.<BigDecimal>get("netto")), BigDecimal.ZERO))
			.where(completate.toPredicate(v, q, cb));
		Object[] base = em.createQuery(q).getSingleResult();

		CriteriaQuery<BigDecimal> qc = cb.createQuery(BigDecimal.class);
		Root<AllocazioneVendita> a = qc.from(AllocazioneVendita.class);
		Join<Object, Object> va = a.join("rigaVendita").join("vendita");
		Subquery<Long> ids = qc.subquery(Long.class);
		Root<Vendita> sv = ids.from(Vendita.class);
		ids.select(sv.get("id")).where(completate.toPredicate(sv, qc, cb));
		Expression<BigDecimal> costoRiga = cb.prod(a.<BigDecimal>get("costoUnitario"),
				cb.toBigDecimal(a.<Integer>get("quantita")));
		qc.select(cb.coalesce(cb.sum(costoRiga), BigDecimal.ZERO))
			.where(va.get("id").in(ids));
		BigDecimal costo = Importi.importo(em.createQuery(qc).getSingleResult());

		CriteriaQuery<BigDecimal> qr = cb.createQuery(BigDecimal.class);
		Root<Vendita> vr = qr.from(Vendita.class);
		qr.select(cb.coalesce(cb.sum(vr.<BigDecimal>get("costiReso")), BigDecimal.ZERO))
			.where(filtro.toSpecification()
				.and((root, query, b) -> b.equal(root.get("stato"), StatoVendita.RESA))
				.toPredicate(vr, qr, cb));
		BigDecimal costiResi = Importi.importo(em.createQuery(qr).getSingleResult());

		BigDecimal netto = Importi.importo((BigDecimal) base[2]);
		return new Totali((Long) base[0], Importi.importo((BigDecimal) base[1]), netto, costo, costiResi,
				netto.subtract(costo).subtract(costiResi));
	}

	/**
	 * Valida le righe compilate: articolo dall'elenco, quantità, prezzo e disponibilità in
	 * magazzino (contando, in modifica, i pezzi già allocati a questa vendita).
	 */
	public void validaRighe(VenditaForm form, Vendita esistente, Errors errors) {
		List<RigaForm> righe = form.getRighe();
		Map<Long, Integer> richiestiPerArticolo = new LinkedHashMap<>();
		Map<Long, Integer> primaRigaArticolo = new HashMap<>();
		boolean almenoUna = false;
		for (int i = 0; i < righe.size(); i++) {
			RigaForm riga = righe.get(i);
			if (riga == null || riga.isVuota()) {
				continue;
			}
			almenoUna = true;
			String campo = "righe[" + i + "].";
			Long articoloId = ElencoArticoli.idDaEtichetta(riga.getArticoloEtichetta());
			riga.setArticoloId(articoloId);
			boolean articoloValido = articoloId != null && articoloRepository.existsById(articoloId);
			if (!articoloValido) {
				errors.rejectValue(campo + "articoloEtichetta", "articolo", "Scegli un articolo dall'elenco");
			}
			boolean quantitaValida = riga.getQuantita() != null && riga.getQuantita() >= 1;
			if (!quantitaValida) {
				errors.rejectValue(campo + "quantita", "quantita", "Almeno 1");
			}
			if (riga.getPrezzoUnitario() == null || riga.getPrezzoUnitario().signum() < 0) {
				errors.rejectValue(campo + "prezzoUnitario", "prezzo", "Prezzo non valido");
			}
			if (!articoloValido || !quantitaValida) {
				continue;
			}
			if (riga.getLottoId() != null) {
				LottoDisponibile lotto = disponibilita(articoloId, esistente).stream()
					.filter(l -> l.lotto().getId().equals(riga.getLottoId()))
					.findFirst()
					.orElse(null);
				if (lotto == null) {
					errors.rejectValue(campo + "lottoId", "lotto", "Lotto non disponibile per questo articolo");
				}
				else if (riga.getQuantita() > lotto.pezzi()) {
					errors.rejectValue(campo + "lottoId", "lotto", "Nel lotto restano " + lotto.pezzi() + " pezzi");
				}
			}
			richiestiPerArticolo.merge(articoloId, riga.getQuantita(), Integer::sum);
			primaRigaArticolo.putIfAbsent(articoloId, i);
		}
		richiestiPerArticolo.forEach((articoloId, richiesti) -> {
			int disponibili = disponibilita(articoloId, esistente).stream().mapToInt(LottoDisponibile::pezzi).sum();
			if (richiesti > disponibili) {
				errors.rejectValue("righe[" + primaRigaArticolo.get(articoloId) + "].quantita", "disponibilita",
						disponibili == 0 ? "Nessun pezzo disponibile" : "Disponibili solo " + disponibili + " pezzi");
			}
		});
		if (!almenoUna) {
			errors.reject("righe", "Aggiungi almeno un articolo venduto");
		}
	}

	/**
	 * Crea (id null) o riscrive una vendita completata: in modifica la merce viene prima
	 * rimessa in magazzino e poi riallocata.
	 */
	@Transactional
	public Vendita salva(Long id, VenditaForm form) {
		Piattaforma piattaforma = piattaformaRepository.findById(form.getPiattaformaId())
			.orElseThrow(() -> new RisorsaNonTrovataException("Piattaforma", form.getPiattaformaId()));
		Vendita vendita;
		if (id == null) {
			vendita = new Vendita(form.getDataVendita(), piattaforma);
		}
		else {
			vendita = trova(id);
			if (!vendita.isCompletata()) {
				throw new OperazioneNonConsentitaException("Si possono modificare solo le vendite completate");
			}
			vendita.svuota();
			em.flush();
		}
		vendita.setDataVendita(form.getDataVendita());
		vendita.setPiattaforma(piattaforma);
		vendita.setAcquirente(vuotoComeNull(form.getAcquirente()));
		vendita.setNote(vuotoComeNull(form.getNote()));
		vendita.setSpedizioneIncassata(form.getSpedizioneIncassata());
		vendita.setCostoSpedizione(form.getCostoSpedizione());
		vendita.setCostoImballaggio(form.getCostoImballaggio());
		vendita.setAltriCosti(form.getAltriCosti());

		// prima le righe con lotto scelto, poi quelle FIFO, così il FIFO non consuma quei lotti
		List<RigaForm> ordinate = new ArrayList<>(form.righeCompilate());
		ordinate.sort(Comparator.comparing((RigaForm r) -> r.getLottoId() == null));
		Map<RigaForm, RigaVendita> create = new HashMap<>();
		for (RigaForm dati : ordinate) {
			Articolo articolo = articolo(dati);
			RigaVendita riga = new RigaVendita(articolo, dati.getQuantita(), dati.getPrezzoUnitario());
			if (dati.getLottoId() != null) {
				RigaAcquisto lotto = rigaAcquistoRepository.findById(dati.getLottoId())
					.filter(l -> l.getArticolo().getId().equals(articolo.getId()))
					.filter(l -> l.getStato() != StatoOggetto.IN_GRADING)
					.filter(l -> l.getQuantitaResidua() >= dati.getQuantita())
					.orElseThrow(() -> new OperazioneNonConsentitaException(
							"Il lotto scelto per \"" + articolo.getNome() + "\" non ha abbastanza pezzi"));
				riga.allocaDa(lotto, dati.getQuantita());
			}
			else {
				allocaFifo(riga);
			}
			create.put(dati, riga);
		}
		// le righe mantengono l'ordine del form
		form.righeCompilate().forEach(dati -> vendita.aggiungiRiga(create.get(dati)));

		vendita.setCommissioniAutomatiche(form.isCommissioniAutomatiche());
		Commissioni commissioni = commissioni(piattaforma, form, vendita.getTotaleLordo());
		vendita.setCommissioniPiattaforma(commissioni.piattaforma());
		vendita.setCommissioniPagamento(commissioni.pagamento());
		return venditaRepository.save(vendita);
	}

	@Transactional
	public void annulla(Long id, StornoForm form) {
		Vendita vendita = trova(id);
		verificaCompletata(vendita);
		vendita.annulla(form.getData(), vuotoComeNull(form.getMotivo()));
	}

	@Transactional
	public void reso(Long id, StornoForm form) {
		Vendita vendita = trova(id);
		verificaCompletata(vendita);
		vendita.registraReso(form.getData(), form.getCostiReso(), vuotoComeNull(form.getMotivo()));
	}

	/** Elimina una vendita annullata (la merce è già tornata in magazzino). */
	@Transactional
	public void elimina(Long id) {
		Vendita vendita = trova(id);
		if (vendita.getStato() != StatoVendita.ANNULLATA) {
			throw new OperazioneNonConsentitaException("Si possono eliminare solo le vendite annullate");
		}
		venditaRepository.delete(vendita);
	}

	/**
	 * Calcolo senza salvare: lordo, commissioni, netto e costo stimato della merce secondo
	 * l'allocazione che verrebbe fatta.
	 *
	 * @param venditaId vendita in modifica, i cui pezzi tornano disponibili; null per una nuova
	 */
	public Anteprima anteprima(VenditaForm form, Long venditaId) {
		Vendita esistente = venditaId == null ? null : venditaRepository.findById(venditaId).orElse(null);
		BigDecimal lordo = BigDecimal.ZERO;
		BigDecimal costo = BigDecimal.ZERO;
		boolean costoCompleto = true;
		Map<Long, Integer> usati = new HashMap<>();
		List<Anteprima.Riga> righe = new ArrayList<>();
		for (RigaForm dati : form.righeCompilate()) {
			Long articoloId = ElencoArticoli.idDaEtichetta(dati.getArticoloEtichetta());
			boolean completa = dati.getQuantita() != null && dati.getQuantita() > 0 && dati.getPrezzoUnitario() != null;
			BigDecimal totale = completa ? dati.getPrezzoUnitario().multiply(BigDecimal.valueOf(dati.getQuantita()))
					: null;
			BigDecimal costoRiga = null;
			if (completa && articoloId != null) {
				costoRiga = costoStimato(articoloId, dati.getLottoId(), dati.getQuantita(), esistente, usati);
			}
			if (totale != null) {
				lordo = lordo.add(totale);
			}
			if (costoRiga != null) {
				costo = costo.add(costoRiga);
			}
			else {
				costoCompleto = false;
			}
			righe.add(new Anteprima.Riga(etichettaBreve(dati.getArticoloEtichetta()), dati.getQuantita(), totale,
					costoRiga == null ? null : Importi.importo(costoRiga)));
		}
		lordo = Importi.importo(lordo);
		Piattaforma piattaforma = form.getPiattaformaId() == null ? null
				: piattaformaRepository.findById(form.getPiattaformaId()).orElse(null);
		Commissioni commissioni = commissioni(piattaforma, form, lordo);
		BigDecimal spedizione = Importi.importo(form.getSpedizioneIncassata());
		BigDecimal costiVendita = Importi.importo(form.getCostoSpedizione())
			.add(Importi.importo(form.getCostoImballaggio()))
			.add(Importi.importo(form.getAltriCosti()));
		BigDecimal netto = lordo.add(spedizione).subtract(commissioni.totale()).subtract(costiVendita);
		BigDecimal costoVenduto = costoCompleto && !righe.isEmpty() ? Importi.importo(costo) : null;
		BigDecimal profitto = costoVenduto == null ? null : netto.subtract(costoVenduto);
		BigDecimal roi = profitto == null ? null : Importi.percentuale(profitto, costoVenduto);
		return new Anteprima(righe, lordo, spedizione, commissioni, costiVendita, netto, costoVenduto, profitto, roi,
				piattaforma != null && form.isCommissioniAutomatiche());
	}

	/** Lotti utilizzabili per l'articolo con i pezzi disponibili, in ordine FIFO. */
	private List<LottoDisponibile> disponibilita(Long articoloId, Vendita esistente) {
		Map<RigaAcquisto, Integer> pezzi = new LinkedHashMap<>();
		rigaAcquistoRepository.findDisponibiliFifo(articoloId).forEach(l -> pezzi.put(l, l.getQuantitaResidua()));
		if (esistente != null && esistente.isCompletata()) {
			esistente.getRighe().stream()
				.flatMap(r -> r.getAllocazioni().stream())
				.filter(a -> a.getRigaAcquisto().getArticolo().getId().equals(articoloId))
				.forEach(a -> pezzi.merge(a.getRigaAcquisto(), a.getQuantita(), Integer::sum));
		}
		return pezzi.entrySet().stream()
			.map(e -> new LottoDisponibile(e.getKey(), e.getValue()))
			.sorted(Comparator.comparing((LottoDisponibile l) -> l.lotto().getAcquisto().getDataAcquisto())
				.thenComparing(l -> l.lotto().getId()))
			.toList();
	}

	private BigDecimal costoStimato(Long articoloId, Long lottoId, int quantita, Vendita esistente,
			Map<Long, Integer> usati) {
		int restanti = quantita;
		BigDecimal costo = BigDecimal.ZERO;
		for (LottoDisponibile disponibile : disponibilita(articoloId, esistente)) {
			RigaAcquisto lotto = disponibile.lotto();
			if (lottoId != null && !lotto.getId().equals(lottoId)) {
				continue;
			}
			int liberi = disponibile.pezzi() - usati.getOrDefault(lotto.getId(), 0);
			int presi = Math.min(liberi, restanti);
			if (presi <= 0) {
				continue;
			}
			usati.merge(lotto.getId(), presi, Integer::sum);
			costo = costo.add(lotto.getCostoUnitarioEffettivo().multiply(BigDecimal.valueOf(presi)));
			restanti -= presi;
			if (restanti == 0) {
				return costo;
			}
		}
		return null;
	}

	private void allocaFifo(RigaVendita riga) {
		int restanti = riga.getQuantita();
		for (RigaAcquisto lotto : rigaAcquistoRepository.findDisponibiliFifo(riga.getArticolo().getId())) {
			int presi = Math.min(lotto.getQuantitaResidua(), restanti);
			if (presi > 0) {
				riga.allocaDa(lotto, presi);
				restanti -= presi;
			}
			if (restanti == 0) {
				return;
			}
		}
		throw new OperazioneNonConsentitaException(
				"Pezzi insufficienti in magazzino per \"" + riga.getArticolo().getNome() + "\"");
	}

	private static Commissioni commissioni(Piattaforma piattaforma, VenditaForm form, BigDecimal lordo) {
		if (form.isCommissioniAutomatiche() && piattaforma != null) {
			return piattaforma.calcolaCommissioni(lordo.add(Importi.nonNullo(form.getSpedizioneIncassata())));
		}
		return new Commissioni(Importi.importo(form.getCommissioniPiattaforma()),
				Importi.importo(form.getCommissioniPagamento()));
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

	private static void verificaCompletata(Vendita vendita) {
		if (!vendita.isCompletata()) {
			throw new OperazioneNonConsentitaException(
					"La vendita è già " + vendita.getStato().getEtichetta().toLowerCase());
		}
	}

	private static void inizializza(Vendita vendita) {
		vendita.getRighe().forEach(r -> {
			r.getArticolo().getNome();
			r.getAllocazioni().size();
		});
	}

	private static String etichettaBreve(String etichetta) {
		if (etichetta == null || etichetta.isBlank()) {
			return "—";
		}
		return etichetta.replaceFirst("\\s*#\\d+\\s*$", "");
	}

	private static String vuotoComeNull(String valore) {
		return valore == null || valore.isBlank() ? null : valore.trim();
	}

	private record LottoDisponibile(RigaAcquisto lotto, int pezzi) {
	}

	/** Totali dello storico filtrato. */
	public record Totali(long vendite, BigDecimal lordo, BigDecimal netto, BigDecimal costoVenduto,
			BigDecimal costiResi, BigDecimal profitto) {
	}

	/** Calcolo mostrato nel form mentre si compila. */
	public record Anteprima(List<Riga> righe, BigDecimal lordo, BigDecimal spedizioneIncassata,
			Commissioni commissioni, BigDecimal costiVendita, BigDecimal netto, BigDecimal costoVenduto,
			BigDecimal profitto, BigDecimal roi, boolean commissioniCalcolate) {

		public record Riga(String articolo, Integer quantita, BigDecimal totale, BigDecimal costo) {
		}

	}

}
