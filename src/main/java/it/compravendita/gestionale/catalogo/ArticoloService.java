package it.compravendita.gestionale.catalogo;

import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import it.compravendita.gestionale.acquisti.RigaAcquistoRepository;
import it.compravendita.gestionale.common.FileStorage;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;
import it.compravendita.gestionale.spese.SpesaRepository;

@Service
@Transactional(readOnly = true)
public class ArticoloService {

	static final String CARTELLA_FOTO = "articoli";

	private final ArticoloRepository articoloRepository;

	private final BrandRepository brandRepository;

	private final RigaAcquistoRepository rigaAcquistoRepository;

	private final SpesaRepository spesaRepository;

	private final FileStorage fileStorage;

	public ArticoloService(ArticoloRepository articoloRepository, BrandRepository brandRepository,
			RigaAcquistoRepository rigaAcquistoRepository, SpesaRepository spesaRepository,
			FileStorage fileStorage) {
		this.articoloRepository = articoloRepository;
		this.brandRepository = brandRepository;
		this.rigaAcquistoRepository = rigaAcquistoRepository;
		this.spesaRepository = spesaRepository;
		this.fileStorage = fileStorage;
	}

	public Page<Articolo> cerca(FiltroArticoli filtro, Pageable pageable) {
		return articoloRepository.findAll(filtro.toSpecification(), pageable);
	}

	public Articolo trova(Long id) {
		return articoloRepository.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Articolo", id));
	}

	/**
	 * Crea (id null) o aggiorna un articolo.
	 *
	 * @param attributi attributi di categoria già validati da {@link SchemaAttributi}
	 * @param foto nuova foto, opzionale
	 */
	@Transactional
	public Articolo salva(Long id, ArticoloForm form, Map<String, Object> attributi, MultipartFile foto) {
		Brand brand = form.getBrandId() == null ? null
				: brandRepository.findById(form.getBrandId())
					.orElseThrow(() -> new RisorsaNonTrovataException("Brand", form.getBrandId()));

		Articolo articolo = id == null ? new Articolo(form.getNome().trim(), form.getCategoria(), brand) : trova(id);
		articolo.setNome(form.getNome().trim());
		articolo.setCategoria(form.getCategoria());
		articolo.setBrand(brand);
		articolo.setLingua(vuotoComeNull(form.getLingua()));
		articolo.setCondizione(form.getCondizione());
		articolo.setNote(vuotoComeNull(form.getNote()));
		articolo.setAttributi(attributi);

		String fotoDaEliminare = null;
		if (foto != null && !foto.isEmpty()) {
			fotoDaEliminare = articolo.getFotoPath();
			articolo.setFotoPath(fileStorage.salvaImmagine(foto, CARTELLA_FOTO));
		}
		else if (form.isRimuoviFoto()) {
			fotoDaEliminare = articolo.getFotoPath();
			articolo.setFotoPath(null);
		}

		Articolo salvato = articoloRepository.save(articolo);
		fileStorage.elimina(fotoDaEliminare);
		return salvato;
	}

	@Transactional
	public void elimina(Long id) {
		Articolo articolo = trova(id);
		if (rigaAcquistoRepository.existsByArticoloId(id) || spesaRepository.existsByArticoloId(id)) {
			throw new OperazioneNonConsentitaException(
					"L'articolo \"" + articolo.getNome() + "\" ha acquisti o spese collegati e non può essere eliminato");
		}
		articoloRepository.delete(articolo);
		fileStorage.elimina(articolo.getFotoPath());
	}

	private static String vuotoComeNull(String valore) {
		return valore == null || valore.isBlank() ? null : valore.trim();
	}

}
