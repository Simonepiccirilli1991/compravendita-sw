package it.compravendita.gestionale.magazzino;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradingRepository extends JpaRepository<Grading, Long> {

	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.articolo", "rigaAcquisto.acquisto",
			"articoloOriginale", "articoloOriginale.brand" })
	Optional<Grading> findConDettagliById(Long id);

	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.articolo", "articoloOriginale" })
	List<Grading> findByStatoOrderByDataInvioDescIdDesc(StatoGrading stato);

	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.articolo", "articoloOriginale" })
	List<Grading> findAllByOrderByDataInvioDescIdDesc();

	/** Grading in cui l'articolo compare come carta inviata o come carta gradata. */
	@EntityGraph(attributePaths = { "rigaAcquisto", "rigaAcquisto.articolo", "articoloOriginale" })
	@Query("""
			select g from Grading g
			where g.articoloOriginale.id = :articoloId or g.rigaAcquisto.articolo.id = :articoloId
			order by g.dataInvio desc, g.id desc
			""")
	List<Grading> findByArticolo(@Param("articoloId") Long articoloId);

	long countByStato(StatoGrading stato);

}
