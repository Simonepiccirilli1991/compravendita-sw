package it.compravendita.gestionale.catalogo;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filtri della lista articoli (parametri della query string).
 */
public class FiltroArticoli {

	private String q;

	private Categoria categoria;

	private Long brandId;

	private Condizione condizione;

	public Specification<Articolo> toSpecification() {
		Specification<Articolo> spec = (root, query, cb) -> cb.conjunction();
		if (q != null && !q.isBlank()) {
			String like = "%" + q.trim().toLowerCase() + "%";
			// nome oppure uno qualsiasi degli attributi (materiale, colore, soggetto, ...)
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("nome")), like),
					cb.like(cb.lower(cb.function("attributi_testo", String.class, root.get("attributi"))), like)));
		}
		if (categoria != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("categoria"), categoria));
		}
		if (brandId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("brand").get("id"), brandId));
		}
		if (condizione != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("condizione"), condizione));
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

	public Condizione getCondizione() {
		return condizione;
	}

	public void setCondizione(Condizione condizione) {
		this.condizione = condizione;
	}

}
