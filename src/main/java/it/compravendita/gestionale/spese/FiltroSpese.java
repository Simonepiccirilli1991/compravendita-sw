package it.compravendita.gestionale.spese;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.criteria.JoinType;

/**
 * Filtri della lista spese generali: periodo, categoria e testo libero su descrizione, note e
 * articolo collegato. Le spese collegate a un lotto d'acquisto non sono spese generali e restano escluse.
 */
public class FiltroSpese {

	private String q;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate da;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate a;

	private CategoriaSpesa categoria;

	public Specification<Spesa> toSpecification() {
		Specification<Spesa> spec = (root, query, cb) -> cb.isNull(root.get("rigaAcquisto"));
		if (q != null && !q.isBlank()) {
			String like = "%" + q.trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("descrizione")), like),
					cb.like(cb.lower(root.get("note")), like),
					cb.like(cb.lower(root.join("articolo", JoinType.LEFT).get("nome")), like)));
		}
		if (da != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataSpesa"), da));
		}
		if (a != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataSpesa"), a));
		}
		if (categoria != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("categoria"), categoria));
		}
		return spec;
	}

	public String getQ() {
		return q;
	}

	public void setQ(String q) {
		this.q = q;
	}

	public LocalDate getDa() {
		return da;
	}

	public void setDa(LocalDate da) {
		this.da = da;
	}

	public LocalDate getA() {
		return a;
	}

	public void setA(LocalDate a) {
		this.a = a;
	}

	public CategoriaSpesa getCategoria() {
		return categoria;
	}

	public void setCategoria(CategoriaSpesa categoria) {
		this.categoria = categoria;
	}

}
