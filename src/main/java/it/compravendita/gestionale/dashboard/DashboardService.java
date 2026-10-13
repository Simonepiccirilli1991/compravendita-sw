package it.compravendita.gestionale.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.magazzino.GiacenzaArticolo;
import it.compravendita.gestionale.magazzino.MagazzinoService;
import it.compravendita.gestionale.magazzino.MagazzinoService.RiepilogoMagazzino;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.Dimensione;
import it.compravendita.gestionale.report.Periodo;
import it.compravendita.gestionale.report.RiepilogoPeriodo;
import it.compravendita.gestionale.report.Risultato;
import it.compravendita.gestionale.report.Velocita;
import it.compravendita.gestionale.report.VenditaInPerdita;

/**
 * Dati della dashboard: KPI del periodo, grafici, hot items e avvisi.
 */
@Service
public class DashboardService {

	static final int TOP = 5;

	private static final DateTimeFormatter MESE = DateTimeFormatter.ofPattern("MMM yy", Locale.ITALY);

	private final CalcoloEconomicoService calcolo;

	private final MagazzinoService magazzinoService;

	public DashboardService(CalcoloEconomicoService calcolo, MagazzinoService magazzinoService) {
		this.calcolo = calcolo;
		this.magazzinoService = magazzinoService;
	}

	public Dashboard carica(Periodo periodo, int giorniFermo) {
		List<Risultato> articoli = calcolo.per(Dimensione.ARTICOLO, periodo).stream()
			.filter(r -> r.vendite() > 0)
			.toList();
		List<Risultato> categorie = calcolo.per(Dimensione.CATEGORIA, periodo);
		List<Risultato> brand = calcolo.per(Dimensione.BRAND, periodo);
		List<Risultato> piattaforme = calcolo.per(Dimensione.PIATTAFORMA, periodo);
		List<Risultato> mesi = calcolo.per(Dimensione.MESE, periodo);
		Page<GiacenzaArticolo> fermi = magazzinoService.fermi(giorniFermo, PageRequest.of(0, TOP));

		Map<String, Object> grafici = new LinkedHashMap<>();
		grafici.put("mesi", serieMensile(mesi, periodo));
		grafici.put("piattaforme", serie(piattaforme, Risultato::lordo));
		grafici.put("categorie", serie(categorie, Risultato::lordo));
		grafici.put("brand", serie(brand.stream().limit(8).toList(), Risultato::profitto));

		return new Dashboard(calcolo.riepilogo(periodo), magazzinoService.riepilogo(), calcolo.tempoMedioVendita(periodo),
				primi(articoli, Comparator.comparingLong(Risultato::pezzi).reversed()),
				primi(articoli, Comparator.comparing(Risultato::profitto).reversed()),
				primi(articoli.stream().filter(r -> r.roi() != null).toList(),
						Comparator.comparing(Risultato::roi).reversed()),
				brand.stream().filter(r -> r.vendite() > 0).limit(TOP).toList(),
				categorie.stream().filter(r -> r.vendite() > 0).toList(),
				calcolo.velocita(Dimensione.CATEGORIA, periodo),
				calcolo.velocita(Dimensione.ARTICOLO, periodo).stream().limit(TOP).toList(),
				fermi.getContent(), fermi.getTotalElements(), magazzinoService.valoreFermi(giorniFermo), giorniFermo,
				calcolo.venditeInPerdita(periodo, TOP), grafici);
	}

	private static List<Risultato> primi(List<Risultato> voci, Comparator<Risultato> ordine) {
		return voci.stream().sorted(ordine.thenComparing(Risultato::etichetta)).limit(TOP).toList();
	}

	/** Etichette e valori per un grafico a torta o a barre. */
	private static Map<String, Object> serie(List<Risultato> voci, Function<Risultato, BigDecimal> valore) {
		Map<String, Object> serie = new LinkedHashMap<>();
		serie.put("etichette", voci.stream().map(Risultato::etichetta).toList());
		serie.put("valori", voci.stream().map(valore).toList());
		return serie;
	}

	/** Fatturato e profitto per mese, con i mesi senza vendite a zero. */
	static Map<String, Object> serieMensile(List<Risultato> mesi, Periodo periodo) {
		Map<YearMonth, Risultato> perMese = mesi.stream()
			.collect(Collectors.toMap(r -> YearMonth.parse(r.chiave()), r -> r));
		List<String> etichette = new ArrayList<>();
		List<BigDecimal> fatturato = new ArrayList<>();
		List<BigDecimal> profitto = new ArrayList<>();
		if (!perMese.isEmpty() || (periodo.getDa() != null && periodo.getA() != null)) {
			YearMonth oggi = YearMonth.now(Formati.FUSO);
			YearMonth inizio = periodo.getDa() != null ? YearMonth.from(periodo.getDa())
					: perMese.keySet().stream().min(Comparator.naturalOrder()).orElse(oggi);
			YearMonth fine = periodo.getA() != null ? min(YearMonth.from(periodo.getA()), oggi)
					: perMese.keySet().stream().max(Comparator.naturalOrder()).orElse(oggi);
			for (YearMonth mese = inizio; !mese.isAfter(fine); mese = mese.plusMonths(1)) {
				Risultato r = perMese.get(mese);
				etichette.add(MESE.format(mese.atDay(1)));
				fatturato.add(r == null ? BigDecimal.ZERO : r.lordo());
				profitto.add(r == null ? BigDecimal.ZERO : r.profitto());
			}
		}
		Map<String, Object> serie = new LinkedHashMap<>();
		serie.put("etichette", etichette);
		serie.put("fatturato", fatturato);
		serie.put("profitto", profitto);
		return serie;
	}

	private static YearMonth min(YearMonth a, YearMonth b) {
		return a.isBefore(b) ? a : b;
	}

	/** Periodo predefinito: gli ultimi 12 mesi, dal primo giorno del mese. */
	public static Periodo ultimi12Mesi() {
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		return new Periodo(oggi.minusMonths(11).withDayOfMonth(1), oggi);
	}

	public record Dashboard(RiepilogoPeriodo riepilogo, RiepilogoMagazzino magazzino, BigDecimal tempoMedioVendita,
			List<Risultato> topPezzi, List<Risultato> topProfitto, List<Risultato> topRoi, List<Risultato> topBrand,
			List<Risultato> categorie, List<Velocita> velocitaCategorie, List<Velocita> articoliPiuVeloci,
			List<GiacenzaArticolo> fermi, long articoliFermi, BigDecimal valoreFermi, int giorniFermo,
			List<VenditaInPerdita> inPerdita, Map<String, Object> grafici) {
	}

}
