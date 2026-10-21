package it.compravendita.gestionale.catalogo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ArticoloRepository extends JpaRepository<Articolo, Long>, JpaSpecificationExecutor<Articolo> {

	@Override
	@EntityGraph(attributePaths = "brand")
	Page<Articolo> findAll(Specification<Articolo> spec, Pageable pageable);

	/** Con il brand, mostrato in dettaglio e form fuori dalla transazione (open-in-view disattivato). */
	@Override
	@EntityGraph(attributePaths = "brand")
	Optional<Articolo> findById(Long id);

	List<Articolo> findByCategoria(Categoria categoria);

	List<Articolo> findByNomeContainingIgnoreCaseOrderByNomeAsc(String testo);

	List<Articolo> findByNomeIgnoreCaseAndCategoria(String nome, Categoria categoria);

	long countByBrandId(Long brandId);

	/** Coppie [brandId, numero articoli]. */
	@Query("select a.brand.id, count(a) from Articolo a where a.brand is not null group by a.brand.id")
	List<Object[]> contaPerBrand();

}
