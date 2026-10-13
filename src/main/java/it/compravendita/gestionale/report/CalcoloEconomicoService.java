package it.compravendita.gestionale.report;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.Importi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Calcoli economici aggregati su vendite, acquisti e magazzino.
 *
 * <ul>
 * <li>Netto = lordo + spedizione incassata − commissioni − spedizione, imballaggio e altri costi</li>
 * <li>Profitto = netto − costo del venduto (FIFO, costi accessori e grading inclusi) − costi dei resi</li>
 * <li>ROI % = profitto / costo del venduto × 100</li>
 * <li>Margine % = profitto / lordo × 100</li>
 * </ul>
 *
 * Il netto e i costi dei resi sono della vendita intera: quando una vendita contiene più
 * articoli vengono ripartiti sulle righe in proporzione al loro incasso (o alle quantità se
 * l'incasso è zero). Le vendite annullate non contano.
 */
@Service
@Transactional(readOnly = true)
public class CalcoloEconomicoService {

	/**
	 * Una riga per ogni riga di vendita non annullata nel periodo, con la quota della vendita
	 * che le spetta e il costo della merce allocata.
	 */
	private static final String RIGHE = """
			with righe as (
			    select v.id as vendita_id, v.data_vendita, v.stato, v.piattaforma_id,
			           rv.id as riga_id, rv.articolo_id, a.nome as articolo_nome, a.categoria, a.brand_id,
			           rv.quantita,
			           rv.prezzo_unitario * rv.quantita as lordo,
			           case when v.totale_lordo > 0 then rv.prezzo_unitario * rv.quantita / v.totale_lordo
			                else cast(rv.quantita as numeric) / sum(rv.quantita) over (partition by v.id)
			           end as quota,
			           v.netto, v.costi_reso,
			           coalesce((select sum(av.quantita * av.costo_unitario) from allocazione_vendita av
			                     where av.riga_vendita_id = rv.id), 0) as costo
			    from riga_vendita rv
			    join vendita v on v.id = rv.vendita_id
			    join articolo a on a.id = rv.articolo_id
			    where v.stato <> 'ANNULLATA' and v.data_vendita between :da and :a
			)
			""";

	private static final String AGGREGATI = """
			count(distinct case when r.stato = 'COMPLETATA' then r.vendita_id end),
			coalesce(sum(case when r.stato = 'COMPLETATA' then r.quantita end), 0),
			coalesce(sum(case when r.stato = 'COMPLETATA' then r.lordo end), 0),
			coalesce(sum(case when r.stato = 'COMPLETATA' then r.netto * r.quota end), 0),
			coalesce(sum(case when r.stato = 'COMPLETATA' then r.costo end), 0),
			coalesce(sum(case when r.stato = 'RESA' then r.costi_reso * r.quota end), 0)
			""";

	/** Pezzi venduti in vendite completate, con i giorni trascorsi dall'acquisto del lotto. */
	private static final String ALLOCAZIONI = """
			with righe as (
			    select av.quantita, v.data_vendita - ac.data_acquisto as giorni, v.data_vendita, v.piattaforma_id,
			           rv.articolo_id, a.nome as articolo_nome, a.categoria, a.brand_id
			    from allocazione_vendita av
			    join riga_vendita rv on rv.id = av.riga_vendita_id
			    join vendita v on v.id = rv.vendita_id
			    join riga_acquisto ra on ra.id = av.riga_acquisto_id
			    join acquisto ac on ac.id = ra.acquisto_id
			    join articolo a on a.id = rv.articolo_id
			    where v.stato = 'COMPLETATA' and v.data_vendita between :da and :a
			)
			""";

	private static final String GIORNI_MEDI = "round(cast(sum(r.quantita * r.giorni) as numeric) / sum(r.quantita), 1)";

	private final EntityManager em;

	public CalcoloEconomicoService(EntityManager em) {
		this.em = em;
	}

	/** Totale delle vendite nel periodo. */
	public Risultato totale(Periodo periodo) {
		Object[] riga = (Object[]) query(RIGHE + "select " + AGGREGATI + " from righe r", periodo).getSingleResult();
		return risultato(null, "Totale", riga, 0);
	}

	/**
	 * Risultati raggruppati per la dimensione indicata. I mesi sono in ordine cronologico, le
	 * altre voci dal profitto più alto.
	 */
	public List<Risultato> per(Dimensione dimensione, Periodo periodo) {
		String sql = RIGHE + "select " + dimensione.chiaveSql() + ", " + dimensione.etichettaSql() + ", " + AGGREGATI
				+ " from righe r" + dimensione.joinSql() + " group by 1, 2";
		@SuppressWarnings("unchecked")
		List<Object[]> righe = query(sql, periodo).getResultList();
		Comparator<Risultato> ordine = dimensione == Dimensione.MESE ? Comparator.comparing(Risultato::chiave)
				: Comparator.comparing(Risultato::profitto).reversed().thenComparing(Risultato::etichetta);
		return righe.stream()
			.map(r -> risultato((String) r[0], etichetta(dimensione, (String) r[1]), r, 2))
			.sorted(ordine)
			.toList();
	}

	/** Giorni medi tra acquisto e vendita dei pezzi venduti nel periodo; null se nessuna vendita. */
	public BigDecimal tempoMedioVendita(Periodo periodo) {
		return (BigDecimal) query(ALLOCAZIONI + "select " + GIORNI_MEDI + " from righe r", periodo).getSingleResult();
	}

	/** Velocità di vendita per voce, dalla più rapida. */
	public List<Velocita> velocita(Dimensione dimensione, Periodo periodo) {
		String sql = ALLOCAZIONI + "select " + dimensione.chiaveSql() + ", " + dimensione.etichettaSql() + ", "
				+ GIORNI_MEDI + ", sum(r.quantita) from righe r" + dimensione.joinSql() + " group by 1, 2 order by 3, 2";
		@SuppressWarnings("unchecked")
		List<Object[]> righe = query(sql, periodo).getResultList();
		return righe.stream()
			.map(r -> new Velocita((String) r[0], etichetta(dimensione, (String) r[1]), (BigDecimal) r[2],
					((Number) r[3]).longValue()))
			.toList();
	}

	/** Vendite completate del periodo chiuse in perdita, dalla peggiore. */
	public List<VenditaInPerdita> venditeInPerdita(Periodo periodo, int limite) {
		Query query = query("""
				select v.id, v.data_vendita, p.nome,
				       (select a.nome from riga_vendita rv join articolo a on a.id = rv.articolo_id
				        where rv.vendita_id = v.id order by rv.id limit 1),
				       v.netto - coalesce(c.costo, 0) as profitto
				from vendita v
				join piattaforma p on p.id = v.piattaforma_id
				left join (select rv.vendita_id, sum(av.quantita * av.costo_unitario) as costo
				           from allocazione_vendita av join riga_vendita rv on rv.id = av.riga_vendita_id
				           group by rv.vendita_id) c on c.vendita_id = v.id
				where v.stato = 'COMPLETATA' and v.data_vendita between :da and :a
				  and v.netto - coalesce(c.costo, 0) < 0
				order by profitto, v.data_vendita desc
				""", periodo);
		query.setMaxResults(limite);
		@SuppressWarnings("unchecked")
		List<Object[]> righe = query.getResultList();
		return righe.stream()
			.map(r -> new VenditaInPerdita(((Number) r[0]).longValue(), data(r[1]), (String) r[2], (String) r[3],
					Importi.importo((BigDecimal) r[4])))
			.toList();
	}

	/** Conto economico del periodo, con perdite di magazzino, spese generali e acquisti. */
	public RiepilogoPeriodo riepilogo(Periodo periodo) {
		BigDecimal perdite = somma("""
				select coalesce(sum(rm.quantita * rm.costo_unitario - rm.importo_recuperato), 0)
				from rettifica_magazzino rm where rm.data_rettifica between :da and :a
				""", periodo);
		BigDecimal spese = somma("""
				select coalesce(sum(s.importo), 0) from spesa s
				where s.riga_acquisto_id is null and s.data_spesa between :da and :a
				""", periodo);
		BigDecimal acquistato = somma("""
				select coalesce(sum(ac.costo_spedizione + ac.commissioni + ac.altri_costi
				    + coalesce((select sum(ra.prezzo_unitario * ra.quantita) from riga_acquisto ra
				                where ra.acquisto_id = ac.id), 0)), 0)
				from acquisto ac where ac.data_acquisto between :da and :a
				""", periodo);
		return new RiepilogoPeriodo(totale(periodo), perdite, spese, acquistato);
	}

	/** Anni con almeno un acquisto o una vendita, dal più recente; l'anno in corso c'è sempre. */
	public List<Integer> anniDisponibili() {
		@SuppressWarnings("unchecked")
		List<Number> anni = em.createNativeQuery("""
				select distinct anno from (
				    select extract(year from data_acquisto) as anno from acquisto
				    union select extract(year from data_vendita) from vendita) t
				order by anno desc
				""").getResultList();
		List<Integer> risultato = new ArrayList<>(anni.stream().map(Number::intValue).toList());
		int corrente = LocalDate.now(Formati.FUSO).getYear();
		if (!risultato.contains(corrente)) {
			risultato.addFirst(corrente);
		}
		return risultato;
	}

	/** Conto dell'anno solare, con le rimanenze di magazzino al 31 dicembre. */
	public ContoAnnuale contoAnnuale(int anno) {
		Periodo periodo = new Periodo(LocalDate.of(anno, 1, 1), LocalDate.of(anno, 12, 31));
		RiepilogoPeriodo riepilogo = riepilogo(periodo);
		Object[] vendite = (Object[]) query("""
				select coalesce(sum(v.spedizione_incassata), 0),
				       coalesce(sum(v.commissioni_piattaforma + v.commissioni_pagamento), 0),
				       coalesce(sum(v.costo_spedizione + v.costo_imballaggio + v.altri_costi), 0)
				from vendita v where v.stato = 'COMPLETATA' and v.data_vendita between :da and :a
				""", periodo).getSingleResult();
		BigDecimal grading = somma("""
				select coalesce(sum(g.costo), 0) from grading g
				where g.stato = 'RIENTRATO' and g.data_rientro between :da and :a
				""", periodo);
		return new ContoAnnuale(anno, riepilogo.acquistato(), grading, riepilogo.vendite(),
				Importi.importo((BigDecimal) vendite[0]), Importi.importo((BigDecimal) vendite[1]),
				Importi.importo((BigDecimal) vendite[2]), riepilogo.perditeRettifiche(), riepilogo.speseGenerali(),
				rimanenze(periodo.fine()));
	}

	/**
	 * Valore al costo dei pezzi in giacenza a fine giornata della data indicata: pezzi acquistati
	 * entro quella data meno quelli venduti (contando come vendute anche le vendite annullate o
	 * rese dopo quella data) e quelli rettificati. Il costo è quello attuale del lotto, grading
	 * compreso.
	 */
	public BigDecimal rimanenze(LocalDate data) {
		return Importi.importo((BigDecimal) em.createNativeQuery("""
				select coalesce(sum(greatest(ra.quantita - coalesce(ven.pezzi, 0) - coalesce(ret.pezzi, 0), 0)
				                    * ra.costo_unitario_effettivo), 0)
				from riga_acquisto ra
				join acquisto ac on ac.id = ra.acquisto_id
				left join (select av.riga_acquisto_id, sum(av.quantita) as pezzi
				           from allocazione_vendita av
				           join riga_vendita rv on rv.id = av.riga_vendita_id
				           join vendita v on v.id = rv.vendita_id
				           where v.data_vendita <= :data and (v.stato = 'COMPLETATA' or v.data_storno > :data)
				           group by av.riga_acquisto_id) ven on ven.riga_acquisto_id = ra.id
				left join (select rm.riga_acquisto_id, sum(rm.quantita) as pezzi
				           from rettifica_magazzino rm where rm.data_rettifica <= :data
				           group by rm.riga_acquisto_id) ret on ret.riga_acquisto_id = ra.id
				where ac.data_acquisto <= :data
				""").setParameter("data", data).getSingleResult());
	}

	/**
	 * Risultato di un acquisto: per ogni riga i pezzi venduti in vendite completate, la quota di
	 * netto incassata e il costo, più le perdite da rettifica e il valore ancora in magazzino.
	 */
	public RisultatoAcquisto perAcquisto(Acquisto acquisto) {
		Query vendite = em.createNativeQuery(RIGHE + """
				select ra.id,
				       coalesce(sum(case when r.riga_id is not null then av.quantita end), 0),
				       coalesce(sum(case when r.riga_id is not null then r.netto * r.quota * av.quantita / r.quantita end), 0),
				       coalesce(sum(case when r.riga_id is not null then av.quantita * av.costo_unitario end), 0)
				from riga_acquisto ra
				left join allocazione_vendita av on av.riga_acquisto_id = ra.id
				left join righe r on r.riga_id = av.riga_vendita_id and r.stato = 'COMPLETATA'
				where ra.acquisto_id = :acquisto
				group by ra.id
				""");
		parametri(vendite, Periodo.sempre());
		vendite.setParameter("acquisto", acquisto.getId());
		Map<Long, RisultatoAcquisto.Riga> righe = new HashMap<>();
		@SuppressWarnings("unchecked")
		List<Object[]> risultati = vendite.getResultList();
		for (Object[] r : risultati) {
			righe.put(((Number) r[0]).longValue(), new RisultatoAcquisto.Riga(((Number) r[1]).intValue(),
					Importi.importo((BigDecimal) r[2]), Importi.importo((BigDecimal) r[3])));
		}

		BigDecimal perdite = Importi.importo((BigDecimal) em.createNativeQuery("""
				select coalesce(sum(rm.quantita * rm.costo_unitario - rm.importo_recuperato), 0)
				from rettifica_magazzino rm join riga_acquisto ra on ra.id = rm.riga_acquisto_id
				where ra.acquisto_id = :acquisto
				""").setParameter("acquisto", acquisto.getId()).getSingleResult());

		BigDecimal costiAggiuntivi = acquisto.getRighe().stream()
			.map(RigaAcquisto::getCostiAggiuntivi)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal valoreResiduo = acquisto.getRighe().stream()
			.map(RigaAcquisto::getValoreResiduo)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		int residui = acquisto.getRighe().stream().mapToInt(RigaAcquisto::getQuantitaResidua).sum();
		return new RisultatoAcquisto(Importi.importo(acquisto.getTotale().add(costiAggiuntivi)),
				acquisto.getTotalePezzi(), righe.values().stream().mapToInt(RisultatoAcquisto.Riga::pezziVenduti).sum(),
				residui,
				righe.values().stream().map(RisultatoAcquisto.Riga::ricavoNetto).reduce(BigDecimal.ZERO, BigDecimal::add),
				righe.values().stream().map(RisultatoAcquisto.Riga::costoVenduto).reduce(BigDecimal.ZERO, BigDecimal::add),
				perdite, Importi.importo(valoreResiduo), righe);
	}

	private Query query(String sql, Periodo periodo) {
		Query query = em.createNativeQuery(sql);
		parametri(query, periodo);
		return query;
	}

	private static void parametri(Query query, Periodo periodo) {
		query.setParameter("da", periodo.inizio());
		query.setParameter("a", periodo.fine());
	}

	private BigDecimal somma(String sql, Periodo periodo) {
		return Importi.importo((BigDecimal) query(sql, periodo).getSingleResult());
	}

	/** Converte una riga di aggregati che inizia alla colonna {@code da}. */
	private static Risultato risultato(String chiave, String etichetta, Object[] riga, int da) {
		return new Risultato(chiave, etichetta, ((Number) riga[da]).longValue(), ((Number) riga[da + 1]).longValue(),
				Importi.importo((BigDecimal) riga[da + 2]), Importi.importo((BigDecimal) riga[da + 3]),
				Importi.importo((BigDecimal) riga[da + 4]), Importi.importo((BigDecimal) riga[da + 5]));
	}

	private static LocalDate data(Object valore) {
		return valore instanceof Date d ? d.toLocalDate() : (LocalDate) valore;
	}

	private static String etichetta(Dimensione dimensione, String valore) {
		if (dimensione == Dimensione.CATEGORIA && valore != null) {
			return Categoria.valueOf(valore).getEtichetta();
		}
		return valore;
	}

}
