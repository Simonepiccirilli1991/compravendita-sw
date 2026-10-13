package it.compravendita.gestionale.importazione;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.Brand;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.catalogo.CampoAttributo;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.Condizione;
import it.compravendita.gestionale.catalogo.SchemaAttributi;
import it.compravendita.gestionale.common.Formati;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.esportazioni.CsvScrittore;
import it.compravendita.gestionale.magazzino.StatoOggetto;

/**
 * Caricamento iniziale dell'inventario da CSV. Ogni riga è un lotto: l'articolo viene cercato
 * nel catalogo (stesso nome, categoria e brand) o creato, e le righe con la stessa data e lo
 * stesso fornitore diventano un unico acquisto. L'importazione avviene solo se tutte le righe
 * sono valide.
 */
@Service
@Transactional(readOnly = true)
public class ImportazioneService {

	static final String NOTA_IMPORT = "Importato da file CSV";

	/** Nomi di colonna riconosciuti (normalizzati) → campo. */
	private static final Map<String, String> COLONNE = Map.ofEntries(Map.entry("nome", "nome"),
			Map.entry("articolo", "nome"), Map.entry("categoria", "categoria"), Map.entry("brand", "brand"),
			Map.entry("franchise", "brand"), Map.entry("lingua", "lingua"), Map.entry("condizione", "condizione"),
			Map.entry("quantita", "quantita"), Map.entry("qta", "quantita"), Map.entry("pezzi", "quantita"),
			Map.entry("prezzo_unitario", "prezzo"), Map.entry("prezzo", "prezzo"), Map.entry("costo", "prezzo"),
			Map.entry("prezzo_acquisto", "prezzo"), Map.entry("data_acquisto", "data"), Map.entry("data", "data"),
			Map.entry("fornitore", "fornitore"), Map.entry("stato", "stato"), Map.entry("note", "note"));

	private static final List<String> OBBLIGATORIE = List.of("nome", "categoria", "quantita", "prezzo");

	private static final DateTimeFormatter DATA_IT = DateTimeFormatter.ofPattern("d/M/yyyy");

	private final ArticoloRepository articoloRepository;

	private final BrandRepository brandRepository;

	private final AcquistoRepository acquistoRepository;

	private final SchemaAttributi schemaAttributi;

	public ImportazioneService(ArticoloRepository articoloRepository, BrandRepository brandRepository,
			AcquistoRepository acquistoRepository, SchemaAttributi schemaAttributi) {
		this.articoloRepository = articoloRepository;
		this.brandRepository = brandRepository;
		this.acquistoRepository = acquistoRepository;
		this.schemaAttributi = schemaAttributi;
	}

	/** Interpreta il file senza salvare nulla. */
	public Analisi analizza(byte[] contenuto) {
		List<List<String>> righe = LettoreCsv.leggi(contenuto);
		List<String> erroriGenerali = new ArrayList<>();
		if (righe.isEmpty()) {
			erroriGenerali.add("Il file è vuoto");
			return new Analisi(List.of(), erroriGenerali, List.of(), 0, Set.of(), 0);
		}
		List<String> intestazione = righe.getFirst();
		Map<String, Integer> campi = new HashMap<>();
		Map<String, Integer> attributi = new LinkedHashMap<>();
		List<String> ignorate = new ArrayList<>();
		Set<String> chiaviAttributo = chiaviAttributo();
		for (int i = 0; i < intestazione.size(); i++) {
			String colonna = normalizza(intestazione.get(i));
			if (colonna.isEmpty()) {
				continue;
			}
			String campo = COLONNE.get(colonna);
			String attributo = attributo(colonna, chiaviAttributo);
			if (campo != null) {
				campi.putIfAbsent(campo, i);
			}
			else if (attributo != null) {
				attributi.put(attributo, i);
			}
			else {
				ignorate.add(intestazione.get(i).trim());
			}
		}
		OBBLIGATORIE.stream().filter(c -> !campi.containsKey(c))
			.forEach(c -> erroriGenerali.add("Manca la colonna obbligatoria \"" + c + "\""));
		if (!erroriGenerali.isEmpty()) {
			return new Analisi(List.of(), erroriGenerali, ignorate, 0, Set.of(), 0);
		}

		Set<String> brandEsistenti = new HashSet<>();
		brandRepository.findAll().forEach(b -> brandEsistenti.add(b.getNome().toLowerCase(Locale.ROOT)));
		Set<String> brandNuovi = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		Set<String> articoliNuovi = new LinkedHashSet<>();
		Set<String> acquisti = new LinkedHashSet<>();
		List<RigaImport> interpretate = new ArrayList<>();
		for (int r = 1; r < righe.size(); r++) {
			RigaImport riga = interpreta(r + 1, righe.get(r), campi, attributi, brandEsistenti);
			interpretate.add(riga);
			if (riga.brandNuovo()) {
				brandNuovi.add(riga.brand());
			}
			if (riga.isArticoloNuovo() && riga.isValida()) {
				articoliNuovi.add(chiaveArticolo(riga));
			}
			acquisti.add(riga.dataAcquisto() + "|" + Objects.toString(riga.fornitore(), "").toLowerCase(Locale.ROOT));
		}
		if (interpretate.isEmpty()) {
			erroriGenerali.add("Il file non contiene righe oltre all'intestazione");
		}
		return new Analisi(interpretate, erroriGenerali, ignorate, articoliNuovi.size(), brandNuovi, acquisti.size());
	}

	/** Importa il file; rifiuta l'operazione se una qualsiasi riga ha errori. */
	@Transactional
	public Esito importa(byte[] contenuto) {
		Analisi analisi = analizza(contenuto);
		if (!analisi.valida()) {
			throw new OperazioneNonConsentitaException("Il file contiene errori: correggili e riprova");
		}
		Map<String, Brand> brand = new HashMap<>();
		Map<String, Articolo> articoli = new HashMap<>();
		Map<String, Acquisto> acquisti = new LinkedHashMap<>();
		int articoliCreati = 0;
		for (RigaImport riga : analisi.righe()) {
			Articolo articolo;
			if (riga.articoloId() != null) {
				articolo = articoloRepository.getReferenceById(riga.articoloId());
			}
			else {
				String chiave = chiaveArticolo(riga);
				articolo = articoli.get(chiave);
				if (articolo == null) {
					articolo = new Articolo(riga.nome(), riga.categoria(), brand(riga.brand(), brand));
					articolo.setLingua(riga.lingua());
					articolo.setCondizione(riga.condizione());
					articolo.setAttributi(riga.attributi());
					articolo = articoloRepository.save(articolo);
					articoli.put(chiave, articolo);
					articoliCreati++;
				}
			}
			String chiaveAcquisto = riga.dataAcquisto() + "|"
					+ Objects.toString(riga.fornitore(), "").toLowerCase(Locale.ROOT);
			Acquisto acquisto = acquisti.computeIfAbsent(chiaveAcquisto, k -> {
				Acquisto nuovo = new Acquisto(riga.dataAcquisto());
				nuovo.setFornitore(riga.fornitore());
				nuovo.setNote(NOTA_IMPORT + " il " + LocalDate.now(Formati.FUSO).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
				return nuovo;
			});
			RigaAcquisto lotto = new RigaAcquisto(articolo, riga.quantita(), riga.prezzoUnitario());
			lotto.setStato(riga.stato());
			acquisto.aggiungiRiga(lotto);
		}
		acquisti.values().forEach(Acquisto::ripartisciCostiAccessori);
		acquistoRepository.saveAll(acquisti.values());
		return new Esito(analisi.righe().size(), acquisti.size(), articoliCreati, analisi.brandNuovi().size());
	}

	/** File di esempio con tutte le colonne riconosciute. */
	public byte[] modello() {
		String[][] righe = {
				{ "nome", "categoria", "brand", "lingua", "condizione", "quantita", "prezzo_unitario", "data_acquisto",
						"fornitore", "stato", "note", "set", "numeroCarta", "enteGrading", "voto", "numeroCertificato",
						"tipoProdotto", "console", "regione", "completezza", "numeroSet" },
				{ "Pikachu Illustrator", "Carta singola", "Pokémon", "JAP", "Near Mint", "2", "15,50", "15/01/2026",
						"Mario Rossi", "In magazzino", "", "Promo", "025", "", "", "", "", "", "", "", "" },
				{ "Charizard Base Set PSA 9", "Carta gradata", "Pokémon", "ENG", "", "1", "450", "20/02/2026",
						"Asta eBay", "In vendita", "", "Base Set", "4/102", "PSA", "9", "12345678", "", "", "", "", "" },
				{ "Super Mario 64", "Videogioco", "Nintendo", "", "Good", "3", "25", "20/02/2026", "Asta eBay",
						"In magazzino", "Retro", "", "", "", "", "", "", "N64", "PAL", "CIB", "" },
				{ "Millennium Falcon", "LEGO", "LEGO", "", "Sigillato", "1", "650", "", "", "", "", "", "", "", "", "",
						"", "", "", "", "75192" } };
		StringBuilder csv = new StringBuilder("﻿");
		for (String[] riga : righe) {
			List<String> campi = new ArrayList<>();
			for (String campo : riga) {
				campi.add(CsvScrittore.campo(campo));
			}
			csv.append(String.join(CsvScrittore.SEPARATORE, campi)).append("\r\n");
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	private RigaImport interpreta(int numero, List<String> valori, Map<String, Integer> campi,
			Map<String, Integer> colonneAttributi, Set<String> brandEsistenti) {
		List<String> errori = new ArrayList<>();
		String nome = valore(valori, campi.get("nome"));
		if (nome == null) {
			errori.add("Nome mancante");
		}
		Categoria categoria = null;
		String testoCategoria = valore(valori, campi.get("categoria"));
		if (testoCategoria == null) {
			errori.add("Categoria mancante");
		}
		else {
			categoria = enumDa(Categoria.values(), testoCategoria, Categoria::getEtichetta);
			if (categoria == null) {
				errori.add("Categoria \"" + testoCategoria + "\" non riconosciuta");
			}
		}
		String brand = valore(valori, campi.get("brand"));
		boolean brandNuovo = brand != null && !brandEsistenti.contains(brand.toLowerCase(Locale.ROOT));

		Condizione condizione = null;
		String testoCondizione = valore(valori, campi.get("condizione"));
		if (testoCondizione != null) {
			condizione = enumDa(Condizione.values(), testoCondizione, Condizione::getEtichetta);
			if (condizione == null) {
				errori.add("Condizione \"" + testoCondizione + "\" non riconosciuta");
			}
		}
		Integer quantita = null;
		String testoQuantita = valore(valori, campi.get("quantita"));
		try {
			quantita = testoQuantita == null ? null : Integer.valueOf(testoQuantita);
		}
		catch (NumberFormatException ex) {
			// gestito sotto
		}
		if (quantita == null || quantita < 1) {
			errori.add("Quantità non valida" + (testoQuantita == null ? "" : ": \"" + testoQuantita + "\""));
			quantita = null;
		}
		BigDecimal prezzo = numero(valore(valori, campi.get("prezzo")));
		if (prezzo == null || prezzo.signum() < 0) {
			errori.add("Prezzo unitario non valido");
			prezzo = null;
		}
		LocalDate data = LocalDate.now(Formati.FUSO);
		String testoData = valore(valori, campi.get("data"));
		if (testoData != null) {
			data = data(testoData);
			if (data == null) {
				errori.add("Data \"" + testoData + "\" non valida (usa gg/mm/aaaa)");
			}
		}
		StatoOggetto stato = StatoOggetto.IN_STOCK;
		String testoStato = valore(valori, campi.get("stato"));
		if (testoStato != null) {
			stato = enumDa(StatoOggetto.iniziali().toArray(StatoOggetto[]::new), testoStato, StatoOggetto::getEtichetta);
			if (stato == null) {
				errori.add("Stato \"" + testoStato + "\" non ammesso (in arrivo, in magazzino, in vendita)");
			}
		}

		Long articoloId = null;
		Map<String, Object> attributi = Map.of();
		if (nome != null && categoria != null) {
			articoloId = articoloEsistente(nome, categoria, brand);
			if (articoloId == null) {
				Map<String, String> grezzi = new HashMap<>();
				colonneAttributi.forEach((chiave, indice) -> {
					String v = valore(valori, indice);
					if (v != null) {
						grezzi.put(chiave, v);
					}
				});
				SchemaAttributi.Risultato validati = schemaAttributi.valida(categoria, grezzi);
				Categoria cat = categoria;
				validati.errori().forEach((chiave, messaggio) -> errori
					.add(schemaAttributi.etichetta(cat, chiave) + ": " + messaggio.toLowerCase(Locale.ITALIAN)));
				attributi = validati.valori();
			}
		}
		return new RigaImport(numero, nome, categoria, brand, valore(valori, campi.get("lingua")), condizione,
				quantita, prezzo, data, valore(valori, campi.get("fornitore")), stato, valore(valori, campi.get("note")),
				attributi, articoloId, brandNuovo, errori);
	}

	private Long articoloEsistente(String nome, Categoria categoria, String brand) {
		return articoloRepository.findByNomeIgnoreCaseAndCategoria(nome, categoria).stream()
			.filter(a -> brand == null ? a.getBrand() == null
					: a.getBrand() != null && a.getBrand().getNome().equalsIgnoreCase(brand))
			.map(Articolo::getId)
			.findFirst()
			.orElse(null);
	}

	private Brand brand(String nome, Map<String, Brand> creati) {
		if (nome == null) {
			return null;
		}
		String chiave = nome.toLowerCase(Locale.ROOT);
		return creati.computeIfAbsent(chiave,
				k -> brandRepository.findByNomeIgnoreCase(nome).orElseGet(() -> brandRepository.save(new Brand(nome))));
	}

	private Set<String> chiaviAttributo() {
		Set<String> chiavi = new LinkedHashSet<>();
		for (Categoria c : Categoria.values()) {
			schemaAttributi.campi(c).stream().map(CampoAttributo::chiave).forEach(chiavi::add);
		}
		return chiavi;
	}

	/** Chiave dell'attributo per una colonna "attr:chiave" o con lo stesso nome di un attributo. */
	private static String attributo(String colonna, Set<String> chiavi) {
		String nome = colonna.startsWith("attr:") ? colonna.substring(5) : colonna;
		String compatto = nome.replace("_", "");
		return chiavi.stream().filter(k -> k.equalsIgnoreCase(compatto)).findFirst().orElse(null);
	}

	private static String chiaveArticolo(RigaImport riga) {
		return riga.nome().toLowerCase(Locale.ROOT) + "|" + riga.categoria() + "|"
				+ Objects.toString(riga.brand(), "").toLowerCase(Locale.ROOT);
	}

	private static String valore(List<String> valori, Integer indice) {
		if (indice == null || indice >= valori.size()) {
			return null;
		}
		String v = valori.get(indice).trim();
		return v.isEmpty() ? null : v;
	}

	/** Intestazione in minuscolo, senza accenti, con "_" al posto degli spazi. */
	static String normalizza(String testo) {
		String senzaAccenti = Normalizer.normalize(testo.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return senzaAccenti.toLowerCase(Locale.ROOT).replaceAll("[\\s.]+", "_");
	}

	/** Accetta "1.234,50", "1234.50", "12,5 €". */
	static BigDecimal numero(String testo) {
		if (testo == null) {
			return null;
		}
		String pulito = testo.replace("€", "").replace(" ", "").replace(" ", "");
		if (pulito.contains(",") && pulito.contains(".")) {
			pulito = pulito.replace(".", "").replace(',', '.');
		}
		else {
			pulito = pulito.replace(',', '.');
		}
		try {
			return new BigDecimal(pulito);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	static LocalDate data(String testo) {
		try {
			return testo.contains("/") ? LocalDate.parse(testo, DATA_IT) : LocalDate.parse(testo);
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}

	/** Valore di un enum dal nome (CARTA_SINGOLA) o dall'etichetta (Carta singola), senza badare a maiuscole. */
	static <E extends Enum<E>> E enumDa(E[] valori, String testo, Function<E, String> etichetta) {
		String cercato = normalizza(testo);
		for (E valore : valori) {
			if (normalizza(valore.name()).equals(cercato) || normalizza(etichetta.apply(valore)).equals(cercato)) {
				return valore;
			}
		}
		return null;
	}

	/**
	 * Risultato dell'analisi del file.
	 *
	 * @param colonneIgnorate colonne dell'intestazione non riconosciute
	 * @param acquisti numero di acquisti che verranno creati (righe raggruppate per data e fornitore)
	 */
	public record Analisi(List<RigaImport> righe, List<String> erroriGenerali, List<String> colonneIgnorate,
			int articoliNuovi, Set<String> brandNuovi, int acquisti) {

		public boolean valida() {
			return erroriGenerali.isEmpty() && righe.stream().allMatch(RigaImport::isValida);
		}

		public long righeConErrori() {
			return righe.stream().filter(r -> !r.isValida()).count();
		}

		public int pezzi() {
			return righe.stream().filter(r -> r.quantita() != null).mapToInt(RigaImport::quantita).sum();
		}

		public BigDecimal totale() {
			return righe.stream().map(RigaImport::totale).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
		}

	}

	public record Esito(int righe, int acquisti, int articoliCreati, int brandCreati) {
	}

}
