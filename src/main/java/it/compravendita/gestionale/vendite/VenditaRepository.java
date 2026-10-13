package it.compravendita.gestionale.vendite;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenditaRepository extends JpaRepository<Vendita, Long>, JpaSpecificationExecutor<Vendita> {

	@Override
	@EntityGraph(attributePaths = "piattaforma")
	Page<Vendita> findAll(Specification<Vendita> spec, Pageable pageable);

	@EntityGraph(attributePaths = "piattaforma")
	Optional<Vendita> findConPiattaformaById(Long id);

	List<Vendita> findByStatoAndDataVenditaBetweenOrderByDataVenditaDesc(StatoVendita stato, LocalDate da,
			LocalDate a);

	/** Righe di vendita di un articolo, dalla più recente. */
	@Query("""
			select r from RigaVendita r join fetch r.vendita v join fetch v.piattaforma
			where r.articolo.id = :articoloId
			order by v.dataVendita desc, v.id desc
			""")
	List<RigaVendita> findRigheByArticolo(@Param("articoloId") Long articoloId);

}
