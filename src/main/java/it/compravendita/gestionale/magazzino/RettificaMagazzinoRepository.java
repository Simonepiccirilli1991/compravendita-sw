package it.compravendita.gestionale.magazzino;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RettificaMagazzinoRepository extends JpaRepository<RettificaMagazzino, Long> {

	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.articolo", "rigaAcquisto.acquisto" })
	Page<RettificaMagazzino> findAllByOrderByDataRettificaDescIdDesc(Pageable pageable);

	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.acquisto" })
	List<RettificaMagazzino> findByRigaAcquistoArticoloIdOrderByDataRettificaDesc(Long articoloId);

}
