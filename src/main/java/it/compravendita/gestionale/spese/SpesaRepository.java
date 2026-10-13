package it.compravendita.gestionale.spese;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpesaRepository extends JpaRepository<Spesa, Long>, JpaSpecificationExecutor<Spesa> {

	List<Spesa> findByDataSpesaBetweenOrderByDataSpesaDesc(LocalDate da, LocalDate a);

	boolean existsByArticoloId(Long articoloId);

	@Override
	@EntityGraph(attributePaths = "articolo")
	Page<Spesa> findAll(Specification<Spesa> spec, Pageable pageable);

	@Override
	@EntityGraph(attributePaths = "articolo")
	List<Spesa> findAll(Specification<Spesa> spec, Sort sort);

	@EntityGraph(attributePaths = "articolo")
	Optional<Spesa> findConArticoloById(Long id);

}
