package it.compravendita.gestionale.acquisti;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtri della lista acquisti: periodo e testo libero su fornitore, canale e articoli.
 */
public class FiltroAcquisti {

	private String q;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate da;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate a;

	public Specification<Acquisto> toSpecification() {
		Specification<Acquisto> spec = (root, query, cb) -> cb.conjunction();
		if (q != null && !q.isBlank()) {
			String like = "%" + q.trim().toLowerCase() + "%";
			spec = spec.and((root, query, cb) -> {
				Subquery<Long> conArticolo = query.subquery(Long.class);
				Root<RigaAcquisto> riga = conArticolo.from(RigaAcquisto.class);
				Join<Object, Object> articolo = riga.join("articolo");
				conArticolo.select(riga.get("acquisto").get("id"))
					.where(cb.like(cb.lower(articolo.get("nome")), like));
				return cb.or(cb.like(cb.lower(root.get("fornitore")), like),
						cb.like(cb.lower(root.get("canale")), like), root.get("id").in(conArticolo));
			});
		}
		if (da != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataAcquisto"), da));
		}
		if (a != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataAcquisto"), a));
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

}
