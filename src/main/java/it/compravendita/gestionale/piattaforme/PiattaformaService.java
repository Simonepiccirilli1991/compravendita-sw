package it.compravendita.gestionale.piattaforme;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.common.Importi;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;

@Service
@Transactional(readOnly = true)
public class PiattaformaService {

	private final PiattaformaRepository piattaformaRepository;

	public PiattaformaService(PiattaformaRepository piattaformaRepository) {
		this.piattaformaRepository = piattaformaRepository;
	}

	public List<Piattaforma> tutte() {
		return piattaformaRepository.findAllByOrderByNomeAsc();
	}

	public List<Piattaforma> attive() {
		return piattaformaRepository.findByAttivaTrueOrderByNomeAsc();
	}

	public Map<Long, Long> contaVendite() {
		return piattaformaRepository.contaVendite();
	}

	public Piattaforma trova(Long id) {
		return piattaformaRepository.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Piattaforma", id));
	}

	@Transactional
	public Piattaforma salva(Long id, PiattaformaForm form) {
		String nome = form.getNome().trim();
		piattaformaRepository.findByNomeIgnoreCase(nome)
			.filter(esistente -> !esistente.getId().equals(id))
			.ifPresent(esistente -> {
				throw new OperazioneNonConsentitaException("Esiste già una piattaforma chiamata \"" + nome + "\"");
			});
		Piattaforma piattaforma = id == null ? new Piattaforma(nome) : trova(id);
		piattaforma.setNome(nome);
		piattaforma.setFeePercentuale(form.getFeePercentuale());
		piattaforma.setFeeFissa(Importi.importo(form.getFeeFissa()));
		piattaforma.setFeePagamentoPercentuale(form.getFeePagamentoPercentuale());
		piattaforma.setFeePagamentoFissa(Importi.importo(form.getFeePagamentoFissa()));
		piattaforma.setAttiva(form.isAttiva());
		piattaforma.setNote(form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().trim());
		return piattaformaRepository.save(piattaforma);
	}

	@Transactional
	public void elimina(Long id) {
		Piattaforma piattaforma = trova(id);
		if (piattaformaRepository.haVendite(id)) {
			throw new OperazioneNonConsentitaException("La piattaforma \"" + piattaforma.getNome()
					+ "\" ha vendite registrate: disattivala invece di eliminarla");
		}
		piattaformaRepository.delete(piattaforma);
	}

}
