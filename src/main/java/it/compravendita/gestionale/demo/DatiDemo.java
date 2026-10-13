package it.compravendita.gestionale.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoForm;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.AcquistoService;
import it.compravendita.gestionale.acquisti.MetodoRipartizione;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloForm;
import it.compravendita.gestionale.catalogo.ArticoloService;
import it.compravendita.gestionale.catalogo.AttributiArticolo;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.Condizione;
import it.compravendita.gestionale.catalogo.SchemaAttributi;
import it.compravendita.gestionale.magazzino.Grading;
import it.compravendita.gestionale.magazzino.GradingService;
import it.compravendita.gestionale.magazzino.InvioGradingForm;
import it.compravendita.gestionale.magazzino.MagazzinoService;
import it.compravendita.gestionale.magazzino.MotivoRettifica;
import it.compravendita.gestionale.magazzino.RettificaForm;
import it.compravendita.gestionale.magazzino.RientroGradingForm;
import it.compravendita.gestionale.magazzino.StatoOggetto;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.spese.CategoriaSpesa;
import it.compravendita.gestionale.spese.Spesa;
import it.compravendita.gestionale.spese.SpesaRepository;
import it.compravendita.gestionale.vendite.StornoForm;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaForm;
import it.compravendita.gestionale.vendite.VenditaService;

/**
 * Genera un anno di attività di esempio (catalogo, acquisti, vendite, grading, resi, rettifiche
 * e spese) passando dai servizi dell'applicazione, così i dati rispettano le stesse regole di
 * quelli inseriti a mano. I numeri casuali hanno un seme fisso: ogni caricamento è identico.
 */
@Component
public class DatiDemo {

	private static final Logger log = LoggerFactory.getLogger(DatiDemo.class);

	private static final List<String> FORNITORI = List.of("Vinted", "Subito", "Mercatino dell'usato",
			"Collezionista privato", "Fiera del fumetto", "eBay", "Negozio locale");

	private final AcquistoRepository acquistoRepository;

	private final AcquistoService acquistoService;

	private final ArticoloService articoloService;

	private final BrandRepository brandRepository;

	private final PiattaformaRepository piattaformaRepository;

	private final VenditaService venditaService;

	private final GradingService gradingService;

	private final MagazzinoService magazzinoService;

	private final SpesaRepository spesaRepository;

	private final SchemaAttributi schemaAttributi;

	public DatiDemo(AcquistoRepository acquistoRepository, AcquistoService acquistoService,
			ArticoloService articoloService, BrandRepository brandRepository,
			PiattaformaRepository piattaformaRepository, VenditaService venditaService,
			GradingService gradingService, MagazzinoService magazzinoService, SpesaRepository spesaRepository,
			SchemaAttributi schemaAttributi) {
		this.acquistoRepository = acquistoRepository;
		this.acquistoService = acquistoService;
		this.articoloService = articoloService;
		this.brandRepository = brandRepository;
		this.piattaformaRepository = piattaformaRepository;
		this.venditaService = venditaService;
		this.gradingService = gradingService;
		this.magazzinoService = magazzinoService;
		this.spesaRepository = spesaRepository;
		this.schemaAttributi = schemaAttributi;
	}

	/**
	 * Carica i dati solo se il database non contiene ancora acquisti.
	 *
	 * @return true se i dati sono stati caricati
	 */
	public boolean caricaSeVuoto() {
		if (acquistoRepository.count() > 0) {
			log.info("Dati demo non caricati: il database contiene già degli acquisti");
			return false;
		}
		genera(LocalDate.now());
		return true;
	}

	/** Genera i dati dell'anno che termina in {@code oggi}. */
	public void genera(LocalDate oggi) {
		Random caso = new Random(42);
		configuraCommissioni();
		Map<String, Modello> catalogo = creaCatalogo();

		int vendite = 0;
		List<Lotto> lotti = new ArrayList<>();
		for (int giorni = 360; giorni >= 20; giorni -= 6 + caso.nextInt(8)) {
			lotti.addAll(acquistoCasuale(oggi.minusDays(giorni), catalogo, caso));
		}
		List<Vendita> create = new ArrayList<>();
		for (Lotto lotto : lotti) {
			for (int pezzo = 0; pezzo < lotto.quantita(); pezzo++) {
				LocalDate data = lotto.data().plusDays(3 + (long) (caso.nextDouble() * caso.nextDouble() * 150));
				if (caso.nextDouble() < 0.78 && !data.isAfter(oggi)) {
					create.add(vendi(lotto, data, caso));
				}
			}
		}
		vendite += create.size();

		// un reso e un annullamento
		Vendita daRendere = create.get(create.size() / 3);
		StornoForm reso = new StornoForm();
		reso.setData(daRendere.getDataVendita().plusDays(9));
		reso.setMotivo("Acquirente: articolo non conforme alla descrizione");
		reso.setCostiReso(new BigDecimal("6.50"));
		venditaService.reso(daRendere.getId(), reso);
		Vendita daAnnullare = create.get(create.size() / 2);
		StornoForm annullamento = new StornoForm();
		annullamento.setData(daAnnullare.getDataVendita().plusDays(4));
		annullamento.setMotivo("L'acquirente non ha pagato");
		venditaService.annulla(daAnnullare.getId(), annullamento);

		vendite += scenarioGrading(oggi, catalogo);
		scenariMagazzino(oggi, catalogo);
		spese(oggi);
		log.info("Dati demo caricati: {} articoli, {} lotti, {} vendite", catalogo.size(), lotti.size(), vendite);
	}

	/** Commissioni indicative, solo per vedere netto e ROI realistici nella demo. */
	private void configuraCommissioni() {
		commissioni("eBay", "8.00", "0.35", "0", "0");
		commissioni("Cardmarket", "5.00", "0", "0", "0");
		commissioni("Vinted", "0", "0", "0", "0");
		commissioni("Subito", "0", "0", "0", "0");
	}

	private void commissioni(String nome, String percentuale, String fissa, String pagamento,
			String pagamentoFisso) {
		piattaformaRepository.findByNomeIgnoreCase(nome).ifPresent(p -> {
			p.setFeePercentuale(new BigDecimal(percentuale));
			p.setFeeFissa(new BigDecimal(fissa));
			p.setFeePagamentoPercentuale(new BigDecimal(pagamento));
			p.setFeePagamentoFissa(new BigDecimal(pagamentoFisso));
			p.setNote("Commissioni dimostrative dei dati demo");
			piattaformaRepository.save(p);
		});
	}

	private Map<String, Modello> creaCatalogo() {
		Map<String, Modello> catalogo = new LinkedHashMap<>();
		carta(catalogo, "Charizard ex 199/165", "Pokémon", "151", "199/165", "Special Illustration Rare", "95", 2);
		carta(catalogo, "Mew ex 151/165", "Pokémon", "151", "151/165", "Ultra Rare", "12", 4);
		carta(catalogo, "Umbreon VMAX 215/203", "Pokémon", "Evolving Skies", "215/203", "Secret Rare", "380", 1);
		carta(catalogo, "Pikachu VMAX 044/185", "Pokémon", "Vivid Voltage", "044/185", "Ultra Rare", "8", 4);
		carta(catalogo, "Giratina V 186/196", "Pokémon", "Lost Origin", "186/196", "Alternate Art", "160", 1);
		carta(catalogo, "Monkey D. Luffy OP01-024", "One Piece", "Romance Dawn", "OP01-024", "Super Rare", "18", 3);
		carta(catalogo, "Shanks OP01-120", "One Piece", "Romance Dawn", "OP01-120", "Secret Rare", "45", 2);
		carta(catalogo, "Yamato OP04-112", "One Piece", "Kingdoms of Intrigue", "OP04-112", "Secret Rare", "22", 3);

		modello(catalogo, "Booster box Pokémon 151 (JP)", Categoria.SEALED_TCG, "Pokémon", "110", 3,
				Condizione.SIGILLATO, Map.of(AttributiArticolo.TIPO_PRODOTTO, "Booster box", AttributiArticolo.SET, "151"));
		modello(catalogo, "Elite Trainer Box Paldean Fates", Categoria.SEALED_TCG, "Pokémon", "48", 4,
				Condizione.SIGILLATO,
				Map.of(AttributiArticolo.TIPO_PRODOTTO, "Elite Trainer Box", AttributiArticolo.SET, "Paldean Fates"));
		modello(catalogo, "Booster box One Piece OP-05", Categoria.SEALED_TCG, "One Piece", "95", 2,
				Condizione.SIGILLATO,
				Map.of(AttributiArticolo.TIPO_PRODOTTO, "Booster box", AttributiArticolo.SET, "Awakening of the New Era"));

		gioco(catalogo, "Super Mario 64", "Nintendo", "Nintendo 64", "CIB", true, "45");
		gioco(catalogo, "The Legend of Zelda: Ocarina of Time", "Nintendo", "Nintendo 64", "Loose", true, "25");
		gioco(catalogo, "Pokémon Rosso", "Nintendo", "Game Boy", "CIB", true, "70");
		gioco(catalogo, "Final Fantasy VII", "Sony PlayStation", "PlayStation", "CIB", true, "30");
		gioco(catalogo, "Sonic the Hedgehog 2", "Sega", "Mega Drive", "CIB", true, "15");
		gioco(catalogo, "Zelda: Tears of the Kingdom", "Nintendo", "Nintendo Switch", "Sigillato", false, "42");

		lego(catalogo, "LEGO Titanic", "10294", "Icons", 2021, "560");
		lego(catalogo, "LEGO Razor Crest", "75292", "Star Wars", 2020, "110");
		lego(catalogo, "LEGO Casa sull'albero", "21318", "Ideas", 2019, "190");

		modello(catalogo, "Console Game Boy Color", Categoria.ALTRO, "Nintendo", "45", 2, Condizione.GOOD, Map.of());
		// usato solo dallo scenario grading
		carta(catalogo, "Charizard Base Set 4/102", "Pokémon", "Base Set", "4/102", "Holo Rare", "180", 0);
		return catalogo;
	}

	private void carta(Map<String, Modello> catalogo, String nome, String brand, String set, String numero,
			String rarita, String prezzo, int maxPezzi) {
		modello(catalogo, nome, Categoria.CARTA_SINGOLA, brand, prezzo, maxPezzi, Condizione.NEAR_MINT,
				Map.of(AttributiArticolo.SET, set, AttributiArticolo.NUMERO_CARTA, numero, AttributiArticolo.RARITA,
						rarita));
	}

	private void gioco(Map<String, Modello> catalogo, String nome, String brand, String console,
			String completezza, boolean retro, String prezzo) {
		Map<String, String> attributi = new HashMap<>(Map.of(AttributiArticolo.CONSOLE, console,
				AttributiArticolo.REGIONE, "PAL", AttributiArticolo.COMPLETEZZA, completezza));
		if (retro) {
			attributi.put(AttributiArticolo.RETRO, "true");
		}
		modello(catalogo, nome, Categoria.VIDEOGIOCO, brand, prezzo, 2,
				completezza.equals("Sigillato") ? Condizione.SIGILLATO : Condizione.GOOD, attributi);
	}

	private void lego(Map<String, Modello> catalogo, String nome, String numeroSet, String tema, int anno,
			String prezzo) {
		modello(catalogo, nome, Categoria.LEGO, "LEGO", prezzo, 1, Condizione.SIGILLATO,
				Map.of(AttributiArticolo.NUMERO_SET, numeroSet, AttributiArticolo.TEMA, tema, AttributiArticolo.ANNO,
						String.valueOf(anno), AttributiArticolo.STATO_SCATOLA, "Perfetta"));
	}

	private void modello(Map<String, Modello> catalogo, String nome, Categoria categoria, String brand,
			String prezzo, int maxPezzi, Condizione condizione, Map<String, String> attributi) {
		ArticoloForm form = new ArticoloForm();
		form.setNome(nome);
		form.setCategoria(categoria);
		form.setBrandId(brandRepository.findByNomeIgnoreCase(brand).orElseThrow().getId());
		form.setCondizione(condizione);
		form.setLingua(categoria == Categoria.SEALED_TCG && nome.contains("(JP)") ? "Giapponese" : "Italiano");
		SchemaAttributi.Risultato validati = schemaAttributi.valida(categoria, attributi);
		if (!validati.valido()) {
			throw new IllegalStateException("Attributi demo non validi per " + nome + ": " + validati.errori());
		}
		Articolo articolo = articoloService.salva(null, form, validati.valori(), null);
		catalogo.put(nome, new Modello(articolo.getId(), nome, categoria, new BigDecimal(prezzo), maxPezzi));
	}

	private List<Lotto> acquistoCasuale(LocalDate data, Map<String, Modello> catalogo, Random caso) {
		List<Modello> acquistabili = catalogo.values().stream().filter(m -> m.maxPezzi() > 0).toList();
		AcquistoForm form = new AcquistoForm();
		form.setDataAcquisto(data);
		String fornitore = FORNITORI.get(caso.nextInt(FORNITORI.size()));
		form.setFornitore(fornitore);
		form.setCanale(fornitore.equals("Vinted") || fornitore.equals("Subito") || fornitore.equals("eBay")
				? "Online" : "Di persona");
		form.setCostoSpedizione(form.getCanale().equals("Online") ? new BigDecimal("5.90") : BigDecimal.ZERO);
		form.setMetodoRipartizione(MetodoRipartizione.PREZZO);

		List<Modello> scelti = new ArrayList<>();
		int righe = 1 + caso.nextInt(3);
		while (scelti.size() < righe) {
			Modello modello = acquistabili.get(caso.nextInt(acquistabili.size()));
			if (!scelti.contains(modello)) {
				scelti.add(modello);
			}
		}
		for (Modello modello : scelti) {
			AcquistoForm.RigaForm riga = new AcquistoForm.RigaForm();
			riga.setArticoloId(modello.id());
			riga.setQuantita(1 + caso.nextInt(modello.maxPezzi()));
			riga.setPrezzoUnitario(arrotonda(modello.prezzo().multiply(fattore(caso, 0.80, 1.10))));
			form.getRighe().add(riga);
		}
		return lotti(acquistoService.salva(null, form, null), scelti);
	}

	private static List<Lotto> lotti(Acquisto acquisto, List<Modello> modelli) {
		List<Lotto> lotti = new ArrayList<>();
		for (int i = 0; i < acquisto.getRighe().size(); i++) {
			RigaAcquisto riga = acquisto.getRighe().get(i);
			lotti.add(new Lotto(riga.getId(), modelli.get(i), acquisto.getDataAcquisto(), riga.getQuantita(),
					riga.getCostoUnitarioEffettivo()));
		}
		return lotti;
	}

	private Vendita vendi(Lotto lotto, LocalDate data, Random caso) {
		// una vendita su dieci è in perdita
		BigDecimal ricarico = caso.nextDouble() < 0.1 ? fattore(caso, 0.70, 0.95) : fattore(caso, 1.25, 2.10);
		String piattaforma = piattaforma(lotto.modello().categoria(), caso);
		return vendita(lotto.modello(), lotto.id(), data, piattaforma,
				arrotonda(lotto.costoUnitario().multiply(ricarico)), caso);
	}

	private Vendita vendita(Modello modello, Long lottoId, LocalDate data, String nomePiattaforma,
			BigDecimal prezzo, Random caso) {
		Piattaforma piattaforma = piattaformaRepository.findByNomeIgnoreCase(nomePiattaforma).orElseThrow();
		boolean carta = modello.categoria() == Categoria.CARTA_SINGOLA || modello.categoria() == Categoria.CARTA_GRADATA;
		VenditaForm form = new VenditaForm();
		form.setDataVendita(data);
		form.setPiattaformaId(piattaforma.getId());
		form.setRiferimentoOrdine(nomePiattaforma.substring(0, 2).toUpperCase() + "-" + (10000 + caso.nextInt(90000)));
		switch (nomePiattaforma) {
			case "eBay" -> spedizione(form, "5.90", carta ? "2.50" : "6.90");
			case "Cardmarket" -> spedizione(form, carta ? "1.30" : "9.90", carta ? "1.15" : "8.50");
			default -> spedizione(form, "0", "0");
		}
		form.setCostoImballaggio(new BigDecimal(carta ? "0.40" : "1.50"));
		VenditaForm.RigaForm riga = new VenditaForm.RigaForm();
		riga.setArticoloId(modello.id());
		riga.setQuantita(1);
		riga.setPrezzoUnitario(prezzo);
		riga.setLottoId(lottoId);
		form.getRighe().add(riga);
		return venditaService.salva(null, form);
	}

	private static void spedizione(VenditaForm form, String incassata, String costo) {
		form.setSpedizioneIncassata(new BigDecimal(incassata));
		form.setCostoSpedizione(new BigDecimal(costo));
	}

	private static String piattaforma(Categoria categoria, Random caso) {
		double x = caso.nextDouble();
		return switch (categoria) {
			case CARTA_SINGOLA -> x < 0.6 ? "Cardmarket" : x < 0.85 ? "eBay" : "Vinted";
			case CARTA_GRADATA -> x < 0.7 ? "eBay" : "Fiera";
			case SEALED_TCG -> x < 0.5 ? "Cardmarket" : x < 0.8 ? "eBay" : "Fiera";
			case VIDEOGIOCO -> x < 0.4 ? "eBay" : x < 0.7 ? "Vinted" : "Subito";
			case LEGO -> x < 0.5 ? "eBay" : x < 0.8 ? "Subito" : "Privato";
			case ALTRO -> x < 0.5 ? "Subito" : "eBay";
		};
	}

	/** Una carta mandata a PSA e venduta gradata, più un grading ancora in corso. */
	private int scenarioGrading(LocalDate oggi, Map<String, Modello> catalogo) {
		Modello charizard = catalogo.get("Charizard Base Set 4/102");
		Lotto lotto = acquistoSingolo(oggi.minusDays(230), charizard, "180.00", "Collezionista privato",
				StatoOggetto.IN_STOCK);

		InvioGradingForm invio = new InvioGradingForm();
		invio.setEnte("PSA");
		invio.setServizio("Value");
		invio.setDataInvio(oggi.minusDays(220));
		invio.setCostoPrevisto(new BigDecimal("25.00"));
		Grading grading = gradingService.invia(lotto.id(), invio);

		RientroGradingForm rientro = new RientroGradingForm();
		rientro.setDataRientro(oggi.minusDays(150));
		rientro.setNomeArticolo("Charizard Base Set 4/102 PSA 9");
		rientro.setVoto(new BigDecimal("9"));
		rientro.setNumeroCertificato("84512377");
		rientro.setCosto(new BigDecimal("32.00"));
		Articolo gradata = gradingService.rientro(grading.getId(), rientro);

		Modello modelloGradata = new Modello(gradata.getId(), gradata.getNome(), Categoria.CARTA_GRADATA,
				new BigDecimal("212"), 0);
		vendita(modelloGradata, grading.getRigaAcquisto().getId(), oggi.minusDays(120), "eBay",
				new BigDecimal("540.00"), new Random(7));

		Lotto umbreon = acquistoSingolo(oggi.minusDays(60), catalogo.get("Umbreon VMAX 215/203"), "360.00",
				"Collezionista privato", StatoOggetto.IN_STOCK);
		InvioGradingForm inCorso = new InvioGradingForm();
		inCorso.setEnte("CGC");
		inCorso.setDataInvio(oggi.minusDays(25));
		inCorso.setCostoPrevisto(new BigDecimal("28.00"));
		gradingService.invia(umbreon.id(), inCorso);
		return 1;
	}

	/** Una rettifica per merce danneggiata, un articolo fermo da mesi e merce ancora in arrivo. */
	private void scenariMagazzino(LocalDate oggi, Map<String, Modello> catalogo) {
		Lotto mew = acquistoSingolo(oggi.minusDays(170), catalogo.get("Mew ex 151/165"), "11.00", "Vinted",
				StatoOggetto.IN_VENDITA);
		RettificaForm rettifica = new RettificaForm();
		rettifica.setData(oggi.minusDays(160));
		rettifica.setQuantita(1);
		rettifica.setMotivo(MotivoRettifica.DANNEGGIATO);
		rettifica.setNote("Angolo piegato durante la spedizione");
		magazzinoService.registraRettifica(mew.id(), rettifica);

		acquistoSingolo(oggi.minusDays(280), catalogo.get("LEGO Titanic"), "520.00", "Negozio locale",
				StatoOggetto.IN_VENDITA);
		acquistoSingolo(oggi.minusDays(3), catalogo.get("Booster box One Piece OP-05"), "92.00", "eBay",
				StatoOggetto.IN_ARRIVO);
	}

	private Lotto acquistoSingolo(LocalDate data, Modello modello, String prezzo, String fornitore,
			StatoOggetto stato) {
		AcquistoForm form = new AcquistoForm();
		form.setDataAcquisto(data);
		form.setFornitore(fornitore);
		AcquistoForm.RigaForm riga = new AcquistoForm.RigaForm();
		riga.setArticoloId(modello.id());
		riga.setQuantita(1);
		riga.setPrezzoUnitario(new BigDecimal(prezzo));
		riga.setStato(stato);
		form.getRighe().add(riga);
		return lotti(acquistoService.salva(null, form, null), List.of(modello)).getFirst();
	}

	private void spese(LocalDate oggi) {
		for (int mese = 11; mese >= 0; mese--) {
			LocalDate data = oggi.minusMonths(mese).withDayOfMonth(1);
			spesaRepository.save(new Spesa(data, CategoriaSpesa.ABBONAMENTO, "Abbonamento negozio eBay",
					new BigDecimal("19.50")));
			if (mese % 2 == 0) {
				spesaRepository.save(new Spesa(data.plusDays(9), CategoriaSpesa.MATERIALI,
						"Bustine, toploader e scatole", new BigDecimal("34.80")));
			}
		}
		spesaRepository.save(new Spesa(oggi.minusMonths(5), CategoriaSpesa.FIERA, "Tavolo alla fiera del fumetto",
				new BigDecimal("80.00")));
	}

	private static BigDecimal fattore(Random caso, double minimo, double massimo) {
		return BigDecimal.valueOf(minimo + caso.nextDouble() * (massimo - minimo));
	}

	/** Prezzi "da annuncio": multipli di 50 centesimi. */
	private static BigDecimal arrotonda(BigDecimal valore) {
		BigDecimal mezzi = valore.multiply(BigDecimal.TWO).setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE);
		return mezzi.divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
	}

	private record Modello(Long id, String nome, Categoria categoria, BigDecimal prezzo, int maxPezzi) {
	}

	private record Lotto(Long id, Modello modello, LocalDate data, int quantita, BigDecimal costoUnitario) {
	}

}
