package it.compravendita.gestionale.esportazioni;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.FiltroAcquisti;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.SchemaAttributi;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.magazzino.FiltroMagazzino;
import it.compravendita.gestionale.report.CalcoloEconomicoService;
import it.compravendita.gestionale.report.ContoAnnuale;
import it.compravendita.gestionale.spese.FiltroSpese;
import it.compravendita.gestionale.spese.Spesa;
import it.compravendita.gestionale.spese.SpesaService;
import it.compravendita.gestionale.vendite.FiltroVendite;
import it.compravendita.gestionale.vendite.RigaVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;

/**
 * Prepara le tabelle da esportare in CSV o Excel, con gli stessi filtri delle liste.
 */
@Service
@Transactional(readOnly = true)
public class EsportazioneService {

	private final AcquistoRepository acquistoRepository;

	private final VenditaRepository venditaRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final CalcoloEconomicoService calcolo;

	private final SchemaAttributi schemaAttributi;

	private final SpesaService spesaService;

	public EsportazioneService(AcquistoRepository acquistoRepository, VenditaRepository venditaRepository,
			RigaAcquistoRepository rigaAcquistoRepository, CalcoloEconomicoService calcolo,
			SchemaAttributi schemaAttributi, SpesaService spesaService) {
		this.acquistoRepository = acquistoRepository;
		this.venditaRepository = venditaRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.calcolo = calcolo;
		this.schemaAttributi = schemaAttributi;
		this.spesaService = spesaService;
	}

	/** Una riga per ogni riga d'acquisto. */
	public Tabella acquisti(FiltroAcquisti filtro) {
		Tabella tabella = new Tabella("Acquisti")
			.intero("N. acquisto").data("Data").testo("Fornitore").testo("Canale")
			.testo("Articolo").testo("Categoria").testo("Brand").testo("Dettagli")
			.intero("Quantità").importo("Prezzo unitario").importo("Totale merce").importo("Quota costi accessori")
			.importo("Costi aggiuntivi").importo("Costo unitario effettivo").intero("Disponibili").testo("Stato")
			.testo("Note acquisto");
		for (Acquisto acquisto : acquistoRepository.findAll(filtro.toSpecification(),
				Sort.by("dataAcquisto", "id"))) {
			for (RigaAcquisto r : acquisto.getRighe()) {
				Articolo a = r.getArticolo();
				tabella.riga(acquisto.getId(), acquisto.getDataAcquisto(), acquisto.getFornitore(), acquisto.getCanale(),
						a.getNome(), a.getCategoria().getEtichetta(), brand(a), schemaAttributi.riepilogo(a),
						r.getQuantita(), r.getPrezzoUnitario(), r.getTotalePrezzo(), r.getQuotaCostiAccessori(),
						r.getCostiAggiuntivi(), r.getCostoUnitarioEffettivo(), r.getQuantitaResidua(),
						r.getStato().getEtichetta(), acquisto.getNote());
			}
		}
		return tabella;
	}

	/** Spese generali, in ordine di data. */
	public Tabella spese(FiltroSpese filtro) {
		Tabella tabella = new Tabella("Spese").data("Data").testo("Categoria").testo("Descrizione")
			.importo("Importo").testo("Articolo").testo("Note");
		for (Spesa s : spesaService.tutte(filtro)) {
			tabella.riga(s.getDataSpesa(), s.getCategoria().getEtichetta(), s.getDescrizione(), s.getImporto(),
					s.getArticolo() != null ? s.getArticolo().getNome() : null, s.getNote());
		}
		return tabella;
	}

	/** Una riga per vendita, con il conto economico. */
	public Tabella vendite(FiltroVendite filtro) {
		Tabella tabella = new Tabella("Vendite")
			.intero("N. vendita").data("Data").testo("Stato").testo("Piattaforma").testo("Acquirente")
			.testo("N. ordine").testo("Articoli").intero("Pezzi").importo("Lordo").importo("Spedizione incassata")
			.importo("Commissione piattaforma").importo("Commissione pagamento").importo("Spedizione")
			.importo("Imballaggio").importo("Altri costi").importo("Netto").importo("Costo merce")
			.importo("Costi reso").importo("Profitto").colonna("ROI %", Tabella.Tipo.PERCENTUALE)
			.data("Data annullamento/reso");
		for (Vendita v : venditaRepository.findAll(filtro.toSpecification(), Sort.by("dataVendita", "id"))) {
			String articoli = v.getRighe().stream()
				.map(r -> r.getQuantita() + " x " + r.getArticolo().getNome())
				.collect(Collectors.joining(", "));
			tabella.riga(v.getId(), v.getDataVendita(), v.getStato().getEtichetta(), v.getPiattaforma().getNome(),
					v.getAcquirente(), v.getRiferimentoOrdine(), articoli, v.getTotalePezzi(), v.getTotaleLordo(),
					v.getSpedizioneIncassata(), v.getCommissioniPiattaforma(), v.getCommissioniPagamento(),
					v.getCostoSpedizione(), v.getCostoImballaggio(), v.getAltriCosti(), v.getNetto(),
					v.isCompletata() ? v.getCostoVenduto() : null, v.getCostiReso(), v.getProfitto(), v.getRoi(),
					v.getDataStorno());
		}
		return tabella;
	}

	/** Righe di vendita con il costo della merce, utili per analisi per articolo. */
	public Tabella righeVendita(FiltroVendite filtro) {
		Tabella tabella = new Tabella("Righe vendite")
			.intero("N. vendita").data("Data").testo("Stato").testo("Piattaforma").testo("Articolo")
			.testo("Categoria").testo("Brand").intero("Quantità").importo("Prezzo unitario").importo("Totale")
			.importo("Costo merce");
		for (Vendita v : venditaRepository.findAll(filtro.toSpecification(), Sort.by("dataVendita", "id"))) {
			for (RigaVendita r : v.getRighe()) {
				Articolo a = r.getArticolo();
				tabella.riga(v.getId(), v.getDataVendita(), v.getStato().getEtichetta(), v.getPiattaforma().getNome(),
						a.getNome(), a.getCategoria().getEtichetta(), brand(a), r.getQuantita(), r.getPrezzoUnitario(),
						r.getTotale(), r.getCostoVenduto());
			}
		}
		return tabella;
	}

	/** Lotti in magazzino (con gli stessi filtri della vista lotti). */
	public Tabella magazzino(FiltroMagazzino filtro) {
		Tabella tabella = new Tabella("Magazzino")
			.testo("Articolo").testo("Categoria").testo("Brand").testo("Dettagli").data("Data acquisto")
			.testo("Fornitore").testo("Stato").intero("Disponibili").intero("Quantità acquistata")
			.importo("Costo unitario").importo("Valore a costo").intero("Giorni in magazzino");
		LocalDate oggi = LocalDate.now(Formati.FUSO);
		for (RigaAcquisto r : rigaAcquistoRepository.findAll(filtro.toSpecificationLotti(),
				Sort.by("articolo.nome", "id"))) {
			Articolo a = r.getArticolo();
			tabella.riga(a.getNome(), a.getCategoria().getEtichetta(), brand(a), schemaAttributi.riepilogo(a),
					r.getAcquisto().getDataAcquisto(), r.getAcquisto().getFornitore(), r.getStato().getEtichetta(),
					r.getQuantitaResidua(), r.getQuantita(), r.getCostoUnitarioEffettivo(), r.getValoreResiduo(),
					ChronoUnit.DAYS.between(r.getAcquisto().getDataAcquisto(), oggi));
		}
		return tabella;
	}

	/** Conto annuale: una colonna per anno, una riga per voce. */
	public Tabella annuale(List<Integer> anni) {
		List<ContoAnnuale> conti = anni.stream().map(calcolo::contoAnnuale).toList();
		Tabella tabella = new Tabella("Riepilogo annuale").testo("Voce");
		conti.forEach(c -> tabella.importo(String.valueOf(c.anno())));
		voce(tabella, "Acquisti (merce e costi accessori)", conti, ContoAnnuale::acquistato);
		voce(tabella, "Costi di grading", conti, ContoAnnuale::costiGrading);
		voce(tabella, "Fatturato merce", conti, c -> c.vendite().lordo());
		voce(tabella, "Spedizioni incassate", conti, ContoAnnuale::spedizioniIncassate);
		voce(tabella, "Commissioni", conti, ContoAnnuale::commissioni);
		voce(tabella, "Spedizioni, imballaggi e altri costi", conti, ContoAnnuale::costiVendita);
		voce(tabella, "Netto incassato", conti, c -> c.vendite().netto());
		voce(tabella, "Costo della merce venduta", conti, c -> c.vendite().costoVenduto());
		voce(tabella, "Costi dei resi", conti, c -> c.vendite().costiResi());
		voce(tabella, "Profitto sulle vendite", conti, c -> c.vendite().profitto());
		voce(tabella, "Perdite di magazzino", conti, ContoAnnuale::perditeRettifiche);
		voce(tabella, "Spese generali", conti, ContoAnnuale::speseGenerali);
		voce(tabella, "Profitto netto", conti, ContoAnnuale::profittoNetto);
		voce(tabella, "Rimanenze al 31/12 (al costo)", conti, ContoAnnuale::rimanenzeFinali);
		return tabella;
	}

	private static void voce(Tabella tabella, String nome, List<ContoAnnuale> conti,
			Function<ContoAnnuale, Object> valore) {
		Object[] riga = new Object[conti.size() + 1];
		riga[0] = nome;
		for (int i = 0; i < conti.size(); i++) {
			riga[i + 1] = valore.apply(conti.get(i));
		}
		tabella.riga(riga);
	}

	private static String brand(Articolo articolo) {
		return articolo.getBrand() == null ? null : articolo.getBrand().getNome();
	}

}
