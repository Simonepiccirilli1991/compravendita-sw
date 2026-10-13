package it.compravendita.gestionale.magazzino;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Categoria;

/**
 * Filtri delle viste di magazzino (giacenze per articolo e lotti).
 */
public class FiltroMagazzino {

	/** Ordinamenti disponibili per le giacenze. */
	public enum Ordine {

		NOME("Nome"), VALORE("Valore più alto"), GIORNI("Fermi da più tempo"), PEZZI("Più pezzi");

		private final String etichetta;

		Ordine(String etichetta) {
			this.etichetta = etichetta;
		}

		public String getEtichetta() {
			return etichetta;
		}

	}

	private String q;

	private Categoria categoria;

	private Long brandId;

	/** Solo per la vista lotti: null = tutti i lotti con pezzi disponibili. */
	private StatoOggetto stato;

	private Ordine ordine = Ordine.NOME;

	/** Solo lotti acquistati prima di questa data (merce ferma). */
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate acquistatoPrima;

	/** Lotti: pezzi residui > 0, oppure qualunque lotto nello stato richiesto. */
	public Specification<RigaAcquisto> toSpecificationLotti() {
		Specification<RigaAcquisto> spec = stato == null
				? (root, query, cb) -> cb.greaterThan(root.get("quantitaResidua"), 0)
				: (root, query, cb) -> cb.equal(root.get("stato"), stato);
		if (q != null && !q.isBlank()) {
			String like = "%" + q.trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("articolo").get("nome")), like));
		}
		if (categoria != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("articolo").get("categoria"), categoria));
		}
		if (brandId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("articolo").get("brand").get("id"), brandId));
		}
		return spec;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public Long getBrandId() {
		return brandId;
	}

	public void setBrandId(Long brandId) {
		this.brandId = brandId;
	}

	public StatoOggetto getStato() {
		return stato;
	}

	public void setStato(StatoOggetto stato) {
		this.stato = stato;
	}

	public LocalDate getAcquistatoPrima() {
		return acquistatoPrima;
	}

	public void setAcquistatoPrima(LocalDate acquistatoPrima) {
		this.acquistatoPrima = acquistatoPrima;
	}

	public Ordine getOrdine() {
		return ordine;
	}

	public void setOrdine(Ordine ordine) {
		this.ordine = ordine != null ? ordine : Ordine.NOME;
	}

}
