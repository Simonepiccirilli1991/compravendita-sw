package it.compravendita.gestionale.magazzino;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.AttributiArticolo;
import it.compravendita.gestionale.catalogo.CampoAttributo;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.catalogo.SchemaAttributi;
import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;

/**
 * Flusso di grading: invio di una carta singola, rientro come carta gradata (nuovo articolo
 * con ente, voto e certificato) e costo del grading aggiunto al costo della carta.
 */
@Service
@Transactional(readOnly = true)
public class GradingService {

	private final GradingRepository gradingRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final ArticoloRepository articoloRepository;

	private final SchemaAttributi schemaAttributi;

	public GradingService(GradingRepository gradingRepository, RigaAcquistoRepository rigaAcquistoRepository,
			ArticoloRepository articoloRepository, SchemaAttributi schemaAttributi) {
		this.gradingRepository = gradingRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.articoloRepository = articoloRepository;
		this.schemaAttributi = schemaAttributi;
	}

	/** Enti ammessi, gli stessi dell'attributo "ente" delle carte gradate. */
	public List<String> enti() {
		return schemaAttributi.campi(Categoria.CARTA_GRADATA).stream()
			.filter(c -> c.chiave().equals(AttributiArticolo.ENTE_GRADING))
			.findFirst()
			.map(CampoAttributo::opzioni)
			.orElse(List.of());
	}

	public List<Grading> elenco(StatoGrading stato) {
		return stato == null ? gradingRepository.findAllByOrderByDataInvioDescIdDesc()
				: gradingRepository.findByStatoOrderByDataInvioDescIdDesc(stato);
	}

	public List<Grading> perArticolo(Long articoloId) {
		return gradingRepository.findByArticolo(articoloId);
	}

	public long inCorso() {
		return gradingRepository.countByStato(StatoGrading.INVIATO);
	}

	public Grading trova(Long id) {
		return gradingRepository.findConDettagliById(id)
			.orElseThrow(() -> new RisorsaNonTrovataException("Grading", id));
	}

	/** Vero se dal lotto si può mandare una carta a gradare. */
	public static boolean inviabile(RigaAcquisto lotto) {
		return lotto.getArticolo().getCategoria() == Categoria.CARTA_SINGOLA && lotto.getQuantitaResidua() > 0
				&& (lotto.getStato() == StatoOggetto.IN_STOCK || lotto.getStato() == StatoOggetto.IN_VENDITA);
	}

	/** Come {@link #inviabile(RigaAcquisto)}, per i template. */
	public boolean puoInviare(RigaAcquisto lotto) {
		return inviabile(lotto);
	}

	/**
	 * Manda a gradare un pezzo del lotto. Se il lotto ha più pezzi, la carta viene separata in un
	 * nuovo lotto dello stesso acquisto con lo stesso costo unitario.
	 */
	@Transactional
	public Grading invia(Long lottoId, InvioGradingForm form) {
		RigaAcquisto lotto = rigaAcquistoRepository.findConDettagliById(lottoId)
			.orElseThrow(() -> new RisorsaNonTrovataException("Lotto", lottoId));
		if (!inviabile(lotto)) {
			throw new OperazioneNonConsentitaException(
					"Si possono mandare a gradare solo carte singole disponibili in magazzino o in vendita");
		}
		if (!enti().contains(form.getEnte())) {
			throw new OperazioneNonConsentitaException("Ente di grading non valido");
		}
		RigaAcquisto carta = lotto.getAcquisto().scorporaPezzo(lotto);
		rigaAcquistoRepository.saveAndFlush(carta);

		Grading grading = new Grading(carta, form.getEnte(), form.getDataInvio());
		grading.setServizio(vuotoComeNull(form.getServizio()));
		grading.setNumeroOrdine(vuotoComeNull(form.getNumeroOrdine()));
		grading.setCosto(form.getCostoPrevisto());
		grading.setNote(vuotoComeNull(form.getNote()));
		return gradingRepository.save(grading);
	}

	/**
	 * Registra il rientro: crea l'articolo "carta gradata" (stesso brand, lingua, set e numero
	 * della carta originale) e vi sposta il lotto, aggiungendo il costo del grading.
	 */
	@Transactional
	public Articolo rientro(Long gradingId, RientroGradingForm form) {
		Grading grading = trova(gradingId);
		if (!grading.isInviato()) {
			throw new OperazioneNonConsentitaException("Questo grading non è in corso");
		}
		Articolo originale = grading.getArticoloOriginale();

		Map<String, String> attributi = new HashMap<>();
		copia(originale, AttributiArticolo.SET, attributi);
		copia(originale, AttributiArticolo.NUMERO_CARTA, attributi);
		attributi.put(AttributiArticolo.ENTE_GRADING, grading.getEnte());
		attributi.put(AttributiArticolo.VOTO, form.getVoto().stripTrailingZeros().toPlainString());
		attributi.put(AttributiArticolo.NUMERO_CERTIFICATO, form.getNumeroCertificato().trim());
		SchemaAttributi.Risultato validati = schemaAttributi.valida(Categoria.CARTA_GRADATA, attributi);
		if (!validati.valido()) {
			throw new OperazioneNonConsentitaException("Dati del grading non validi: " + validati.errori());
		}

		Articolo gradato = new Articolo(form.getNomeArticolo().trim(), Categoria.CARTA_GRADATA, originale.getBrand());
		gradato.setLingua(originale.getLingua());
		gradato.setAttributi(validati.valori());
		gradato.setNote("Gradata da \"" + originale.getNome() + "\" (grading #" + grading.getId() + ")");
		articoloRepository.save(gradato);

		grading.rientra(gradato, form.getDataRientro(), form.getVoto(), form.getNumeroCertificato().trim(),
				Importi.importo(form.getCosto()));
		return gradato;
	}

	@Transactional
	public void annulla(Long gradingId) {
		Grading grading = trova(gradingId);
		if (!grading.isInviato()) {
			throw new OperazioneNonConsentitaException("Questo grading non è in corso");
		}
		grading.annulla();
	}

	private static void copia(Articolo origine, String chiave, Map<String, String> destinazione) {
		Object valore = origine.getAttributo(chiave);
		if (valore != null) {
			destinazione.put(chiave, SchemaAttributi.formatta(valore));
		}
	}

	private static String vuotoComeNull(String valore) {
		return valore == null || valore.isBlank() ? null : valore.trim();
	}

}
