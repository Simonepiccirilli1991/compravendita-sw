package it.compravendita.gestionale.acquisti;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RigaAcquistoRepository extends JpaRepository<RigaAcquisto, Long>, JpaSpecificationExecutor<RigaAcquisto> {

	@Override
	@EntityGraph(attributePaths = { "articolo", "articolo.brand", "acquisto" })
	Page<RigaAcquisto> findAll(Specification<RigaAcquisto> spec, Pageable pageable);

	@EntityGraph(attributePaths = { "articolo", "articolo.brand", "acquisto" })
	Optional<RigaAcquisto> findConDettagliById(Long id);

	/**
	 * Lotti con pezzi vendibili per un articolo, dal più vecchio al più recente (ordine FIFO).
	 */
	@Query("""
			select r from RigaAcquisto r join r.acquisto a
			where r.articolo.id = :articoloId and r.quantitaResidua > 0
			order by a.dataAcquisto asc, r.id asc
			""")
	List<RigaAcquisto> findDisponibiliFifo(@Param("articoloId") Long articoloId);

	/** Tutti i lotti di un articolo, dal più recente. */
	@EntityGraph(attributePaths = { "acquisto", "articolo" })
	@Query("""
			select r from RigaAcquisto r join r.acquisto a
			where r.articolo.id = :articoloId
			order by a.dataAcquisto desc, r.id desc
			""")
	List<RigaAcquisto> findByArticolo(@Param("articoloId") Long articoloId);

	/**
	 * Righe di un acquisto referenziate da vendite, rettifiche o spese: non possono
	 * essere eliminate né modificate.
	 */
	@Query("""
			select r.id from RigaAcquisto r where r.acquisto.id = :acquistoId and (
			    exists (select av.id from AllocazioneVendita av where av.rigaAcquisto = r)
			    or exists (select rm.id from RettificaMagazzino rm where rm.rigaAcquisto = r)
			    or exists (select s.id from Spesa s where s.rigaAcquisto = r))
			""")
	Set<Long> findIdConCollegamenti(@Param("acquistoId") Long acquistoId);

	/** Coppie [articoloId, pezzi vendibili] per gli articoli con merce disponibile. */
	@Query("""
			select r.articolo.id, sum(r.quantitaResidua) from RigaAcquisto r
			where r.quantitaResidua > 0
			group by r.articolo.id
			""")
	List<Object[]> contaPezziVendibili();

	boolean existsByArticoloId(Long articoloId);

}
