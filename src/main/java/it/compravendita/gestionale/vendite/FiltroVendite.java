package it.compravendita.gestionale.vendite;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.catalogo.Categoria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Filtri dello storico vendite. Categoria, brand e testo cercano tra gli articoli venduti.
 */
public class FiltroVendite {

	private String q;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate da;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate a;

	private Long piattaformaId;

	private Categoria categoria;

	private Long brandId;

	private StatoVendita stato;

	public Specification<Vendita> toSpecification() {
		Specification<Vendita> spec = (root, query, cb) -> cb.conjunction();
		if (q != null && !q.isBlank()) {
			String like = "%" + q.trim().toLowerCase() + "%";
			Long numero = numero(q);
			spec = spec.and((root, query, cb) -> cb.or(cb.like(cb.lower(root.get("acquirente")), like),
					numero != null ? cb.equal(root.get("numero"), numero) : cb.disjunction(),
					conArticolo(root, query, cb, articolo -> cb.like(cb.lower(articolo.get("nome")), like))));
		}
		if (categoria != null) {
			spec = spec.and((root, query, cb) -> conArticolo(root, query, cb,
					articolo -> cb.equal(articolo.get("categoria"), categoria)));
		}
		if (brandId != null) {
			spec = spec.and((root, query, cb) -> conArticolo(root, query, cb,
					articolo -> cb.equal(articolo.get("brand").get("id"), brandId)));
		}
		if (da != null) {
			spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dataVendita"), da));
		}
		if (a != null) {
			spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("dataVendita"), a));
		}
		if (piattaformaId != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("piattaforma").get("id"), piattaformaId));
		}
		if (stato != null) {
			spec = spec.and((root, query, cb) -> cb.equal(root.get("stato"), stato));
		}
		return spec;
	}

	/** Numero di vendita cercato ("12" o "#12"), oppure null se il testo non è un numero. */
	private static Long numero(String testo) {
		String cifre = testo.trim().replaceFirst("^#", "");
		if (cifre.isEmpty() || cifre.length() > 18 || !cifre.chars().allMatch(Character::isDigit)) {
			return null;
		}
		return Long.valueOf(cifre);
	}

	/** Vendite con almeno una riga il cui articolo soddisfa la condizione. */
	private static Predicate conArticolo(Root<Vendita> root, CriteriaQuery<?> query, CriteriaBuilder cb,
			java.util.function.Function<Join<Object, Object>, Predicate> condizione) {
		Subquery<Long> sub = query.subquery(Long.class);
		Root<RigaVendita> riga = sub.from(RigaVendita.class);
		Join<Object, Object> articolo = riga.join("articolo");
		sub.select(riga.get("vendita").get("id")).where(condizione.apply(articolo));
		return root.get("id").in(sub);
	}

	public boolean isAttivo() {
		return (q != null && !q.isBlank()) || da != null || a != null || piattaformaId != null || categoria != null
				|| brandId != null || stato != null;
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

	public Long getPiattaformaId() {
		return piattaformaId;
	}

	public void setPiattaformaId(Long piattaformaId) {
		this.piattaformaId = piattaformaId;
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

	public StatoVendita getStato() {
		return stato;
	}

	public void setStato(StatoVendita stato) {
		this.stato = stato;
	}

}
