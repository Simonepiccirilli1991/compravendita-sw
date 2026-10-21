package it.compravendita.gestionale.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
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
import it.compravendita.gestionale.magazzino.MagazzinoService;
import it.compravendita.gestionale.magazzino.MotivoRettifica;
import it.compravendita.gestionale.magazzino.RettificaForm;
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
 * Genera un anno di attività di esempio (catalogo, acquisti, vendite, resi, rettifiche e
 * spese) passando dai servizi dell'applicazione, così i dati rispettano le stesse regole di
 * quelli inseriti a mano. I numeri casuali hanno un seme fisso: ogni caricamento è identico.
 */
@Component
public class DatiDemo {

	private static final Logger log = LoggerFactory.getLogger(DatiDemo.class);

	private static final List<String> FORNITORI = List.of("Vinted", "Subito", "Mercatino dell'usato",
			"Ingrosso Casalinghi", "Svuota cantine", "eBay", "Negozio locale");

	private final AcquistoRepository acquistoRepository;

	private final AcquistoService acquistoService;

	private final ArticoloService articoloService;

	private final BrandRepository brandRepository;

	private final PiattaformaRepository piattaformaRepository;

	private final VenditaService venditaService;

	private final MagazzinoService magazzinoService;

	private final SpesaRepository spesaRepository;

	private final SchemaAttributi schemaAttributi;

	public DatiDemo(AcquistoRepository acquistoRepository, AcquistoService acquistoService,
			ArticoloService articoloService, BrandRepository brandRepository,
			PiattaformaRepository piattaformaRepository, VenditaService venditaService,
			MagazzinoService magazzinoService, SpesaRepository spesaRepository,
			SchemaAttributi schemaAttributi) {
		this.acquistoRepository = acquistoRepository;
		this.acquistoService = acquistoService;
		this.articoloService = articoloService;
		this.brandRepository = brandRepository;
		this.piattaformaRepository = piattaformaRepository;
		this.venditaService = venditaService;
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
		reso.setMotivo("Acquirente: tazza arrivata scheggiata");
		reso.setCostiReso(new BigDecimal("4.90"));
		venditaService.reso(daRendere.getId(), reso);
		Vendita daAnnullare = create.get(create.size() / 2);
		StornoForm annullamento = new StornoForm();
		annullamento.setData(daAnnullare.getDataVendita().plusDays(4));
		annullamento.setMotivo("L'acquirente non ha pagato");
		venditaService.annulla(daAnnullare.getId(), annullamento);

		scenariMagazzino(oggi, catalogo);
		spese(oggi);
		log.info("Dati demo caricati: {} articoli, {} lotti, {} vendite", catalogo.size(), lotti.size(), vendite);
	}

	/** Commissioni indicative, solo per vedere netto e ROI realistici nella demo. */
	private void configuraCommissioni() {
		commissioni("eBay", "8.00", "0.35", "0", "0");
		commissioni("Etsy", "6.50", "0.20", "4.00", "0.30");
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
		tazza(catalogo, "Tazza Topolino vintage", "Disney", "Ceramica", 300, "Bianca", "Topolino", "Classica", "6", 4);
		tazza(catalogo, "Tazza Stitch sagomata", "Disney", "Ceramica", 450, "Azzurra", "Stitch", "Sagomata / 3D", "11", 6);
		tazza(catalogo, "Mug Hogwarts stemmi", "Harry Potter", "Ceramica", 400, "Nera", "Case di Hogwarts",
				"Mug grande", "8", 6);
		tazza(catalogo, "Tazza Avengers logo", "Marvel", "Ceramica", 350, "Rossa", "Avengers", "Classica", "5.50", 6);
		tazza(catalogo, "Tazza Darth Vader 3D", "Star Wars", "Ceramica", 500, "Nera", "Darth Vader", "Sagomata / 3D",
				"14", 3);
		tazza(catalogo, "Tazza Pikachu con coperchio", "Pokémon", "Ceramica", 350, "Gialla", "Pikachu", "Con coperchio",
				"9", 5);
		tazza(catalogo, "Tazza Central Perk", "Friends", "Ceramica", 400, "Bianca", "Central Perk", "Mug grande", "7",
				6);
		tazza(catalogo, "Tazza Thun angioletti", "Thun", "Porcellana", 250, "Bianca", "Angioletti", "Classica", "12", 3);
		tazza(catalogo, "Tazzine espresso Ikea (vintage)", "Ikea", "Gres", 90, "Verde", null, "Espresso", "2.50", 8);

		modello(catalogo, "Tazza termica Hogwarts", Categoria.TAZZA_TERMICA, "Harry Potter", "9", 5,
				Condizione.NUOVO_CONFEZIONATO, Map.of(AttributiArticolo.MATERIALE, "Acciaio inox",
						AttributiArticolo.CAPACITA, "450", AttributiArticolo.SOGGETTO, "Hogwarts"));
		modello(catalogo, "Borraccia Stitch", Categoria.TAZZA_TERMICA, "Disney", "7.50", 6,
				Condizione.NUOVO_CONFEZIONATO, Map.of(AttributiArticolo.MATERIALE, "Acciaio inox",
						AttributiArticolo.CAPACITA, "500", AttributiArticolo.COLORE, "Azzurra"));
		modello(catalogo, "Bicchieri Star Wars (set da 2)", Categoria.BICCHIERE, "Star Wars", "6", 4,
				Condizione.NUOVO_CONFEZIONATO, Map.of(AttributiArticolo.MATERIALE, "Vetro", AttributiArticolo.CAPACITA,
						"300", AttributiArticolo.SOGGETTO, "Personaggi"));
		modello(catalogo, "Bicchiere Nutella vintage", Categoria.BICCHIERE, "Senza licenza", "1.50", 10,
				Condizione.OTTIMO, Map.of(AttributiArticolo.MATERIALE, "Vetro", AttributiArticolo.CAPACITA, "200"));
		modello(catalogo, "Set colazione Hello Kitty", Categoria.SET_REGALO, "Hello Kitty", "12", 3,
				Condizione.NUOVO_CONFEZIONATO, Map.of(AttributiArticolo.NUMERO_PEZZI, "3", AttributiArticolo.CONTENUTO,
						"Tazza, piattino e cucchiaino", AttributiArticolo.SOGGETTO, "Hello Kitty"));
		modello(catalogo, "Confezione regalo Marvel tazza + calzini", Categoria.SET_REGALO, "Marvel", "10", 4,
				Condizione.NUOVO_CONFEZIONATO,
				Map.of(AttributiArticolo.NUMERO_PEZZI, "2", AttributiArticolo.CONTENUTO, "Tazza e calzini"));
		modello(catalogo, "Sottobicchieri in sughero (6 pz)", Categoria.ACCESSORIO, "Senza licenza", "3", 6,
				Condizione.NUOVO_CONFEZIONATO, Map.of(AttributiArticolo.TIPO_ACCESSORIO, "Sottobicchiere"));
		modello(catalogo, "Infusore per tè a forma di sub", Categoria.ACCESSORIO, "Senza licenza", "2", 8,
				Condizione.NUOVO, Map.of(AttributiArticolo.TIPO_ACCESSORIO, "Infusore", AttributiArticolo.MATERIALE,
						"Acciaio inox"));
		modello(catalogo, "Lotto tazze spaiate", Categoria.ALTRO, "Senza licenza", "4", 3, Condizione.BUONO, Map.of());
		return catalogo;
	}

	private void tazza(Map<String, Modello> catalogo, String nome, String brand, String materiale, int capacita,
			String colore, String soggetto, String forma, String prezzo, int maxPezzi) {
		Map<String, String> attributi = new LinkedHashMap<>();
		attributi.put(AttributiArticolo.MATERIALE, materiale);
		attributi.put(AttributiArticolo.CAPACITA, String.valueOf(capacita));
		attributi.put(AttributiArticolo.COLORE, colore);
		if (soggetto != null) {
			attributi.put(AttributiArticolo.SOGGETTO, soggetto);
		}
		attributi.put(AttributiArticolo.FORMA, forma);
		modello(catalogo, nome, Categoria.TAZZA, brand, prezzo, maxPezzi,
				nome.contains("vintage") ? Condizione.OTTIMO : Condizione.NUOVO_CONFEZIONATO, attributi);
	}

	private void modello(Map<String, Modello> catalogo, String nome, Categoria categoria, String brand,
			String prezzo, int maxPezzi, Condizione condizione, Map<String, String> attributi) {
		ArticoloForm form = new ArticoloForm();
		form.setNome(nome);
		form.setCategoria(categoria);
		form.setBrandId(brandRepository.findByNomeIgnoreCase(brand).orElseThrow().getId());
		form.setCondizione(condizione);
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
		boolean piccolo = modello.categoria() == Categoria.ACCESSORIO;
		VenditaForm form = new VenditaForm();
		form.setDataVendita(data);
		form.setPiattaformaId(piattaforma.getId());
		switch (nomePiattaforma) {
			case "eBay" -> spedizione(form, "6.90", piccolo ? "3.50" : "6.50");
			case "Etsy" -> spedizione(form, "5.90", piccolo ? "3.50" : "6.50");
			default -> spedizione(form, "0", "0");
		}
		// le tazze viaggiano con pluriball e scatola rinforzata
		form.setCostoImballaggio(new BigDecimal(piccolo ? "0.40" : "1.20"));
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
			case TAZZA -> x < 0.5 ? "Vinted" : x < 0.75 ? "eBay" : x < 0.9 ? "Etsy" : "Subito";
			case TAZZA_TERMICA, BICCHIERE -> x < 0.6 ? "Vinted" : x < 0.85 ? "eBay" : "Subito";
			case SET_REGALO -> x < 0.4 ? "Vinted" : x < 0.7 ? "Etsy" : x < 0.9 ? "eBay" : "Fiera";
			case ACCESSORIO -> x < 0.7 ? "Vinted" : "Etsy";
			case ALTRO -> x < 0.5 ? "Subito" : "Vinted";
		};
	}

	/** Una rettifica per merce danneggiata, un articolo fermo da mesi e merce ancora in arrivo. */
	private void scenariMagazzino(LocalDate oggi, Map<String, Modello> catalogo) {
		Lotto thun = acquistoSingolo(oggi.minusDays(170), catalogo.get("Tazza Thun angioletti"), "11.00", "Vinted",
				StatoOggetto.IN_VENDITA);
		RettificaForm rettifica = new RettificaForm();
		rettifica.setData(oggi.minusDays(160));
		rettifica.setQuantita(1);
		rettifica.setMotivo(MotivoRettifica.DANNEGGIATO);
		rettifica.setNote("Manico rotto durante la spedizione");
		magazzinoService.registraRettifica(thun.id(), rettifica);

		acquistoSingolo(oggi.minusDays(280), catalogo.get("Tazza Darth Vader 3D"), "13.00", "Negozio locale",
				StatoOggetto.IN_VENDITA);
		acquistoSingolo(oggi.minusDays(3), catalogo.get("Set colazione Hello Kitty"), "11.50", "Ingrosso Casalinghi",
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
			spesaRepository.save(new Spesa(data.plusDays(15), CategoriaSpesa.PUBBLICITA, "Annunci in evidenza Vinted",
					new BigDecimal("6.95")));
			if (mese % 2 == 0) {
				spesaRepository.save(new Spesa(data.plusDays(9), CategoriaSpesa.MATERIALI,
						"Pluriball, scatole e carta da pacchi", new BigDecimal("28.40")));
			}
		}
		spesaRepository.save(new Spesa(oggi.minusMonths(5), CategoriaSpesa.FIERA, "Banco al mercatino di Natale",
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
