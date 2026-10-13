package it.compravendita.gestionale.catalogo;

import static it.compravendita.gestionale.catalogo.AttributiArticolo.ANNO;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.COMPLETEZZA;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.CONSOLE;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.EDIZIONE;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.ENTE_GRADING;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.NUMERO_CARTA;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.NUMERO_CERTIFICATO;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.NUMERO_SET;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.RARITA;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.REGIONE;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.RETRO;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.SET;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.STATO_SCATOLA;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.TEMA;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.TIPO_PRODOTTO;
import static it.compravendita.gestionale.catalogo.AttributiArticolo.VOTO;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import it.compravendita.gestionale.catalogo.CampoAttributo.Tipo;

/**
 * Attributi previsti per ogni {@link Categoria}: costruisce il form dinamico, valida e converte
 * i valori inseriti prima del salvataggio nel JSONB dell'articolo.
 */
@Component
public class SchemaAttributi {

	private final Map<Categoria, List<CampoAttributo>> campi = new EnumMap<>(Categoria.class);

	public SchemaAttributi() {
		CampoAttributo set = CampoAttributo.testo(SET, "Set / espansione");
		CampoAttributo numeroCarta = CampoAttributo.testo(NUMERO_CARTA, "Numero carta");

		campi.put(Categoria.CARTA_SINGOLA, List.of(
				set,
				numeroCarta,
				CampoAttributo.testo(RARITA, "Rarità"),
				CampoAttributo.testo(EDIZIONE, "Edizione")));
		campi.put(Categoria.CARTA_GRADATA, List.of(
				CampoAttributo.scelta(ENTE_GRADING, "Ente di grading", "PSA", "BGS", "CGC", "SGC", "ACE", "Altro")
					.richiesto(),
				CampoAttributo.decimale(VOTO, "Voto", 1, 10).richiesto(),
				CampoAttributo.testo(NUMERO_CERTIFICATO, "N° certificato").richiesto(),
				set,
				numeroCarta));
		campi.put(Categoria.SEALED_TCG, List.of(
				CampoAttributo.scelta(TIPO_PRODOTTO, "Tipo prodotto", "Booster box", "Display", "Booster pack",
						"Elite Trainer Box", "Collection box", "Tin", "Starter deck", "Altro")
					.richiesto(),
				set));
		campi.put(Categoria.VIDEOGIOCO, List.of(
				CampoAttributo.testo(CONSOLE, "Console").richiesto(),
				CampoAttributo.scelta(REGIONE, "Regione", "PAL", "NTSC-U", "NTSC-J"),
				CampoAttributo.scelta(COMPLETEZZA, "Completezza", "Loose", "CIB", "Sigillato"),
				CampoAttributo.siNo(RETRO, "Retro")));
		campi.put(Categoria.LEGO, List.of(
				CampoAttributo.testo(NUMERO_SET, "Numero set").richiesto(),
				CampoAttributo.testo(TEMA, "Tema"),
				CampoAttributo.intero(ANNO, "Anno", 1949, 2100),
				CampoAttributo.scelta(STATO_SCATOLA, "Stato scatola", "Perfetta", "Buona", "Danneggiata")));
		campi.put(Categoria.ALTRO, List.of());
	}

	public List<CampoAttributo> campi(Categoria categoria) {
		return categoria == null ? List.of() : campi.get(categoria);
	}

	/**
	 * Valida e converte i valori inseriti nel form. I campi vuoti vengono omessi,
	 * le chiavi non previste per la categoria ignorate.
	 */
	public Risultato valida(Categoria categoria, Map<String, String> grezzi) {
		Map<String, Object> valori = new LinkedHashMap<>();
		Map<String, String> errori = new LinkedHashMap<>();
		for (CampoAttributo campo : campi(categoria)) {
			String testo = grezzi == null ? null : grezzi.get(campo.chiave());
			testo = testo == null ? "" : testo.trim();
			if (testo.isEmpty() || (campo.tipo() == Tipo.SI_NO && !"true".equals(testo))) {
				if (campo.obbligatorio()) {
					errori.put(campo.chiave(), "Campo obbligatorio");
				}
				continue;
			}
			try {
				valori.put(campo.chiave(), converti(campo, testo));
			}
			catch (IllegalArgumentException ex) {
				errori.put(campo.chiave(), ex.getMessage());
			}
		}
		return new Risultato(valori, errori);
	}

	/** Valori dell'articolo come stringhe, per ripopolare il form. */
	public Map<String, String> valoriPerForm(Map<String, Object> attributi) {
		Map<String, String> valori = new LinkedHashMap<>();
		attributi.forEach((chiave, valore) -> valori.put(chiave, formatta(valore)));
		return valori;
	}

	/** Breve descrizione degli attributi principali, per le liste (es. "PSA · 10 · 12345678"). */
	public String riepilogo(Articolo articolo) {
		return campi(articolo.getCategoria()).stream()
			.map(campo -> {
				Object valore = articolo.getAttributo(campo.chiave());
				if (valore == null) {
					return null;
				}
				return campo.tipo() == Tipo.SI_NO ? (Boolean.TRUE.equals(valore) ? campo.etichetta() : null)
						: formatta(valore);
			})
			.filter(Objects::nonNull)
			.collect(Collectors.joining(" · "));
	}

	/** Valore formattato di un attributo dell'articolo, "—" se assente. */
	public String valore(Articolo articolo, String chiave) {
		Object valore = articolo.getAttributo(chiave);
		return valore == null ? "—" : formatta(valore);
	}

	public String etichetta(Categoria categoria, String chiave) {
		return campi(categoria).stream()
			.filter(campo -> campo.chiave().equals(chiave))
			.map(CampoAttributo::etichetta)
			.findFirst()
			.orElse(chiave);
	}

	public static String formatta(Object valore) {
		if (valore == null) {
			return "";
		}
		if (valore instanceof Boolean b) {
			return b ? "Sì" : "No";
		}
		if (valore instanceof Number n) {
			return new BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
		}
		return valore.toString();
	}

	private Object converti(CampoAttributo campo, String testo) {
		return switch (campo.tipo()) {
			case TESTO -> testo;
			case SI_NO -> Boolean.TRUE;
			case SCELTA -> {
				if (!campo.opzioni().contains(testo)) {
					throw new IllegalArgumentException("Valore non ammesso");
				}
				yield testo;
			}
			case INTERO -> {
				int numero;
				try {
					numero = Integer.parseInt(testo);
				}
				catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Inserisci un numero intero");
				}
				verificaIntervallo(campo, numero);
				yield numero;
			}
			case DECIMALE -> {
				BigDecimal numero;
				try {
					numero = new BigDecimal(testo.replace(',', '.'));
				}
				catch (NumberFormatException ex) {
					throw new IllegalArgumentException("Inserisci un numero");
				}
				verificaIntervallo(campo, numero.doubleValue());
				yield numero;
			}
		};
	}

	private static void verificaIntervallo(CampoAttributo campo, double numero) {
		if ((campo.minimo() != null && numero < campo.minimo())
				|| (campo.massimo() != null && numero > campo.massimo())) {
			throw new IllegalArgumentException(
					"Deve essere tra " + formatta(campo.minimo()) + " e " + formatta(campo.massimo()));
		}
	}

	public record Risultato(Map<String, Object> valori, Map<String, String> errori) {

		public boolean valido() {
			return errori.isEmpty();
		}

	}

}
