package it.compravendita.gestionale.magazzino;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

@Service
@Transactional(readOnly = true)
public class MagazzinoService {

	private static final String STATO = "it.compravendita.gestionale.magazzino.StatoOggetto.";

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final RettificaMagazzinoRepository rettificaRepository;

	private final EntityManager em;

	public MagazzinoService(RigaAcquistoRepository rigaAcquistoRepository,
			RettificaMagazzinoRepository rettificaRepository, EntityManager em) {
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.rettificaRepository = rettificaRepository;
		this.em = em;
	}

	/** Giacenze aggregate per articolo (solo lotti con pezzi residui). */
	public Page<GiacenzaArticolo> giacenze(FiltroMagazzino filtro, Pageable pageable) {
		StringBuilder where = new StringBuilder(" where r.quantitaResidua > 0");
		Map<String, Object> parametri = new HashMap<>();
		if (filtro.getQ() != null && !filtro.getQ().isBlank()) {
			where.append(" and lower(a.nome) like :q");
			parametri.put("q", "%" + filtro.getQ().trim().toLowerCase() + "%");
		}
		if (filtro.getCategoria() != null) {
			where.append(" and a.categoria = :categoria");
			parametri.put("categoria", filtro.getCategoria());
		}
		if (filtro.getBrandId() != null) {
			where.append(" and b.id = :brandId");
			parametri.put("brandId", filtro.getBrandId());
		}
		if (filtro.getAcquistatoPrima() != null) {
			where.append(" and ac.dataAcquisto < :acquistatoPrima");
			parametri.put("acquistatoPrima", filtro.getAcquistatoPrima());
		}
		String from = " from RigaAcquisto r join r.articolo a left join a.brand b join r.acquisto ac";

		String ordine = switch (filtro.getOrdine()) {
			case VALORE -> "sum(r.costoUnitarioEffettivo * r.quantitaResidua) desc, a.nome";
			case GIORNI -> "min(ac.dataAcquisto) asc, a.nome";
			case PEZZI -> "sum(r.quantitaResidua) desc, a.nome";
			case NOME -> "lower(a.nome), a.id";
		};
		TypedQuery<GiacenzaArticolo> query = em.createQuery("select new " + GiacenzaArticolo.class.getName()
				+ "(a.id, a.nome, a.categoria, b.nome, sum(r.quantitaResidua),"
				+ " sum(case when r.stato = " + STATO + "IN_ARRIVO then r.quantitaResidua else 0 end),"
				+ " sum(case when r.stato = " + STATO + "IN_GRADING then r.quantitaResidua else 0 end),"
				+ " sum(case when r.stato = " + STATO + "IN_VENDITA then r.quantitaResidua else 0 end),"
				+ " sum(r.costoUnitarioEffettivo * r.quantitaResidua), min(ac.dataAcquisto))"
				+ from + where + " group by a.id, a.nome, a.categoria, b.nome order by " + ordine,
				GiacenzaArticolo.class);
		TypedQuery<Long> conteggio = em.createQuery("select count(distinct a.id)" + from + where, Long.class);
		parametri.forEach((nome, valore) -> {
			query.setParameter(nome, valore);
			conteggio.setParameter(nome, valore);
		});
		if (pageable.isPaged()) {
			query.setFirstResult((int) pageable.getOffset());
			query.setMaxResults(pageable.getPageSize());
		}
		return new PageImpl<>(query.getResultList(), pageable, conteggio.getSingleResult());
	}

	/**
	 * Merce ferma: pezzi acquistati da più di {@code giorni} giorni e ancora in giacenza,
	 * per articolo, dai più vecchi.
	 */
	public Page<GiacenzaArticolo> fermi(int giorni, Pageable pageable) {
		FiltroMagazzino filtro = new FiltroMagazzino();
		filtro.setAcquistatoPrima(LocalDate.now(Formati.FUSO).minusDays(giorni));
		filtro.setOrdine(FiltroMagazzino.Ordine.GIORNI);
		return giacenze(filtro, pageable);
	}

	/** Valore a costo della merce ferma da più di {@code giorni} giorni. */
	public BigDecimal valoreFermi(int giorni) {
		BigDecimal valore = em.createQuery("select coalesce(sum(r.costoUnitarioEffettivo * r.quantitaResidua), 0)"
				+ " from RigaAcquisto r join r.acquisto ac where r.quantitaResidua > 0 and ac.dataAcquisto < :soglia",
				BigDecimal.class)
			.setParameter("soglia", LocalDate.now(Formati.FUSO).minusDays(giorni))
			.getSingleResult();
		return Importi.importo(valore);
	}

	/** Totali del magazzino: pezzi, valore a costo e pezzi per stato. */
	public RiepilogoMagazzino riepilogo() {
		Object[] riga = em.createQuery("select coalesce(sum(r.quantitaResidua), 0),"
				+ " coalesce(sum(r.costoUnitarioEffettivo * r.quantitaResidua), 0), count(distinct r.articolo.id),"
				+ " coalesce(sum(case when r.stato = " + STATO + "IN_ARRIVO then r.quantitaResidua else 0 end), 0),"
				+ " coalesce(sum(case when r.stato = " + STATO + "IN_GRADING then r.quantitaResidua else 0 end), 0),"
				+ " coalesce(sum(case when r.stato = " + STATO + "IN_VENDITA then r.quantitaResidua else 0 end), 0)"
				+ " from RigaAcquisto r where r.quantitaResidua > 0", Object[].class)
			.getSingleResult();
		return new RiepilogoMagazzino(numero(riga[0]), (BigDecimal) riga[1], numero(riga[2]), numero(riga[3]),
				numero(riga[4]), numero(riga[5]));
	}

	public Page<RigaAcquisto> lotti(FiltroMagazzino filtro, Pageable pageable) {
		return rigaAcquistoRepository.findAll(filtro.toSpecificationLotti(), pageable);
	}

	public RigaAcquisto lotto(Long id) {
		return rigaAcquistoRepository.findConDettagliById(id)
			.orElseThrow(() -> new RisorsaNonTrovataException("Lotto", id));
	}

	public List<RigaAcquisto> lottiArticolo(Long articoloId) {
		return rigaAcquistoRepository.findByArticolo(articoloId);
	}

	/** Cambio di stato manuale (in arrivo → in magazzino → in vendita e ritorno). */
	@Transactional
	public RigaAcquisto cambiaStato(Long lottoId, StatoOggetto nuovoStato) {
		RigaAcquisto lotto = lotto(lottoId);
		if (!lotto.getStato().transizioniManuali().contains(nuovoStato)) {
			throw new OperazioneNonConsentitaException("Non puoi passare da \"" + lotto.getStato().getEtichetta()
					+ "\" a \"" + nuovoStato.getEtichetta() + "\"");
		}
		lotto.setStato(nuovoStato);
		return lotto;
	}

	public Page<RettificaMagazzino> rettifiche(Pageable pageable) {
		return rettificaRepository.findAllByOrderByDataRettificaDescIdDesc(pageable);
	}

	public List<RettificaMagazzino> rettificheArticolo(Long articoloId) {
		return rettificaRepository.findByRigaAcquistoArticoloIdOrderByDataRettificaDesc(articoloId);
	}

	@Transactional
	public RettificaMagazzino registraRettifica(Long lottoId, RettificaForm form) {
		RigaAcquisto lotto = lotto(lottoId);
		if (lotto.getStato() == StatoOggetto.IN_GRADING) {
			throw new OperazioneNonConsentitaException("La carta è in grading: annulla o registra prima il rientro");
		}
		if (form.getQuantita() > lotto.getQuantitaResidua()) {
			throw new OperazioneNonConsentitaException(
					"Puoi rettificare al massimo " + lotto.getQuantitaResidua() + " pezzi");
		}
		String note = form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().trim();
		return rettificaRepository.save(new RettificaMagazzino(lotto, form.getData(), form.getQuantita(),
				form.getMotivo(), form.getImportoRecuperato(), note));
	}

	/** Annulla una rettifica rimettendo i pezzi nel lotto. */
	@Transactional
	public void annullaRettifica(Long id) {
		RettificaMagazzino rettifica = rettificaRepository.findById(id)
			.orElseThrow(() -> new RisorsaNonTrovataException("Rettifica", id));
		try {
			rettifica.annulla();
		}
		catch (IllegalArgumentException ex) {
			throw new OperazioneNonConsentitaException(
					"I pezzi non possono tornare nel lotto: è stato modificato dopo la rettifica");
		}
		rettificaRepository.delete(rettifica);
	}

	private static long numero(Object valore) {
		return valore == null ? 0 : ((Number) valore).longValue();
	}

	public record RiepilogoMagazzino(long pezzi, BigDecimal valore, long articoli, long inArrivo, long inGrading,
			long inVendita) {
	}

}
