package it.compravendita.gestionale.catalogo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;

@Service
@Transactional(readOnly = true)
public class BrandService {

	private final BrandRepository brandRepository;

	private final ArticoloRepository articoloRepository;

	public BrandService(BrandRepository brandRepository, ArticoloRepository articoloRepository) {
		this.brandRepository = brandRepository;
		this.articoloRepository = articoloRepository;
	}

	public List<Brand> tutti() {
		return brandRepository.findAll(Sort.by("nome"));
	}

	public List<Brand> attivi() {
		return brandRepository.findByAttivoTrueOrderByNomeAsc();
	}

	public Brand trova(Long id) {
		return brandRepository.findById(id).orElseThrow(() -> new RisorsaNonTrovataException("Brand", id));
	}

	/** Numero di articoli per id brand (i brand senza articoli non compaiono). */
	public Map<Long, Long> contaArticoliPerBrand() {
		return articoloRepository.contaPerBrand()
			.stream()
			.collect(Collectors.toMap(riga -> (Long) riga[0], riga -> (Long) riga[1]));
	}

	@Transactional
	public Brand salva(Long id, String nome, boolean attivo) {
		String nomePulito = nome == null ? "" : nome.trim();
		if (nomePulito.isEmpty()) {
			throw new OperazioneNonConsentitaException("Inserisci il nome del brand");
		}
		brandRepository.findByNomeIgnoreCase(nomePulito)
			.filter(esistente -> !esistente.getId().equals(id))
			.ifPresent(esistente -> {
				throw new OperazioneNonConsentitaException("Esiste già un brand chiamato \"" + esistente.getNome() + "\"");
			});
		Brand brand = id == null ? new Brand(nomePulito) : trova(id);
		brand.setNome(nomePulito);
		brand.setAttivo(attivo);
		return brandRepository.save(brand);
	}

	@Transactional
	public void elimina(Long id) {
		Brand brand = trova(id);
		long articoli = articoloRepository.countByBrandId(id);
		if (articoli > 0) {
			throw new OperazioneNonConsentitaException("Il brand \"" + brand.getNome() + "\" è usato da " + articoli
					+ " articoli: disattivalo invece di eliminarlo");
		}
		brandRepository.delete(brand);
	}

}
