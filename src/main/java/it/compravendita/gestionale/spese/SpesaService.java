package it.compravendita.gestionale.spese;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.ElencoArticoli;
import it.compravendita.gestionale.common.OperazioneNonConsentitaException;
import it.compravendita.gestionale.common.RisorsaNonTrovataException;

/**
 * Spese generali dell'attività (abbonamenti, materiali, fiere, …): entrano nel profitto netto dei
 * report e della dashboard ma non nel costo dei singoli articoli.
 */
@Service
@Transactional(readOnly = true)
public class SpesaService {

	private final SpesaRepository spesaRepository;

	private final ArticoloRepository articoloRepository;

	public SpesaService(SpesaRepository spesaRepository, ArticoloRepository articoloRepository) {
		this.spesaRepository = spesaRepository;
		this.articoloRepository = articoloRepository;
	}

	public Page<Spesa> cerca(FiltroSpese filtro, Pageable pageable) {
		return spesaRepository.findAll(filtro.toSpecification(), pageable);
	}

	public List<Spesa> tutte(FiltroSpese filtro) {
		return spesaRepository.findAll(filtro.toSpecification(), Sort.by("dataSpesa", "id"));
	}

	/** Totale e ripartizione per categoria delle spese che rispettano il filtro. */
	public Totali totali(FiltroSpese filtro) {
		Map<CategoriaSpesa, BigDecimal> perCategoria = new EnumMap<>(CategoriaSpesa.class);
		BigDecimal totale = BigDecimal.ZERO;
		for (Spesa spesa : spesaRepository.findAll(filtro.toSpecification())) {
			perCategoria.merge(spesa.getCategoria(), spesa.getImporto(), BigDecimal::add);
			totale = totale.add(spesa.getImporto());
		}
		return new Totali(totale, perCategoria);
	}

	public Spesa trova(Long id) {
		Spesa spesa = spesaRepository.findConArticoloById(id)
			.orElseThrow(() -> new RisorsaNonTrovataException("Spesa", id));
		if (spesa.getRigaAcquisto() != null) {
			throw new OperazioneNonConsentitaException("Questa spesa fa parte del costo di un lotto d'acquisto");
		}
		return spesa;
	}

	/** Controlla che l'articolo indicato, se c'è, esista. */
	public void valida(SpesaForm form, Errors errors) {
		if (form.getArticoloEtichetta() != null && !form.getArticoloEtichetta().isBlank()
				&& articolo(form.getArticoloEtichetta()) == null) {
			errors.rejectValue("articoloEtichetta", "articolo", "Scegli un articolo dall'elenco o lascia vuoto");
		}
	}

	@Transactional
	public Spesa salva(Long id, SpesaForm form) {
		Spesa spesa = id == null
				? new Spesa(form.getDataSpesa(), form.getCategoria(), form.getDescrizione().trim(), form.getImporto())
				: trova(id);
		spesa.setDataSpesa(form.getDataSpesa());
		spesa.setCategoria(form.getCategoria());
		spesa.setDescrizione(form.getDescrizione().trim());
		spesa.setImporto(form.getImporto());
		spesa.setArticolo(form.getArticoloEtichetta() == null || form.getArticoloEtichetta().isBlank() ? null
				: articolo(form.getArticoloEtichetta()));
		spesa.setNote(form.getNote() == null || form.getNote().isBlank() ? null : form.getNote().trim());
		return spesaRepository.save(spesa);
	}

	@Transactional
	public void elimina(Long id) {
		spesaRepository.delete(trova(id));
	}

	private Articolo articolo(String etichetta) {
		Long articoloId = ElencoArticoli.idDaEtichetta(etichetta);
		return articoloId == null ? null : articoloRepository.findById(articoloId).orElse(null);
	}

	public record Totali(BigDecimal totale, Map<CategoriaSpesa, BigDecimal> perCategoria) {
	}

}
