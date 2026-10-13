package it.compravendita.gestionale.catalogo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;

/**
 * Elenco degli articoli per i campi di ricerca con suggerimenti (datalist). Ogni voce termina
 * con {@code #id}, così il valore scelto identifica l'articolo anche con nomi uguali.
 */
@Component
public class ElencoArticoli {

	private static final Pattern ID_FINALE = Pattern.compile("#(\\d+)\\s*$");

	private final ArticoloRepository articoloRepository;

	private final SchemaAttributi schemaAttributi;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	public ElencoArticoli(ArticoloRepository articoloRepository, SchemaAttributi schemaAttributi,
			RigaAcquistoRepository rigaAcquistoRepository) {
		this.articoloRepository = articoloRepository;
		this.schemaAttributi = schemaAttributi;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
	}

	@Transactional(readOnly = true)
	public List<Opzione> opzioni() {
		return articoloRepository.findAll(Sort.by("nome")).stream()
			.map(a -> new Opzione(a.getId(), etichetta(a)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<Opzione> opzioni(Categoria categoria) {
		return articoloRepository.findByCategoria(categoria).stream()
			.sorted((x, y) -> x.getNome().compareToIgnoreCase(y.getNome()))
			.map(a -> new Opzione(a.getId(), etichetta(a)))
			.toList();
	}

	/** Solo gli articoli con pezzi vendibili, con la disponibilità nell'etichetta. */
	@Transactional(readOnly = true)
	public List<Opzione> opzioniVendibili() {
		Map<Long, Long> pezzi = new HashMap<>();
		rigaAcquistoRepository.contaPezziVendibili().forEach(r -> pezzi.put((Long) r[0], (Long) r[1]));
		return articoloRepository.findAllById(pezzi.keySet()).stream()
			.sorted((x, y) -> x.getNome().compareToIgnoreCase(y.getNome()))
			.map(a -> new Opzione(a.getId(), etichetta(a, "(" + pezzi.get(a.getId()) + " disp.)")))
			.toList();
	}

	/** Es. "Charizard Base Set · PSA · 9 · 44556677 #12". */
	public String etichetta(Articolo articolo) {
		return etichetta(articolo, null);
	}

	private String etichetta(Articolo articolo, String nota) {
		String riepilogo = schemaAttributi.riepilogo(articolo);
		StringBuilder testo = new StringBuilder(articolo.getNome());
		if (!riepilogo.isBlank()) {
			testo.append(" · ").append(riepilogo);
		}
		if (nota != null) {
			testo.append(' ').append(nota);
		}
		return testo.append(" #").append(articolo.getId()).toString();
	}

	/** Id dell'articolo contenuto in un'etichetta, o null se il testo non ne contiene. */
	public static Long idDaEtichetta(String etichetta) {
		if (etichetta == null) {
			return null;
		}
		Matcher m = ID_FINALE.matcher(etichetta);
		return m.find() ? Long.valueOf(m.group(1)) : null;
	}

	public record Opzione(Long id, String etichetta) {
	}

}
