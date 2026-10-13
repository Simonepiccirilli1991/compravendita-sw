package it.compravendita.gestionale.piattaforme;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PiattaformaRepository extends JpaRepository<Piattaforma, Long> {

	Optional<Piattaforma> findByNomeIgnoreCase(String nome);

	List<Piattaforma> findByAttivaTrueOrderByNomeAsc();

	List<Piattaforma> findAllByOrderByNomeAsc();

	@Query("select count(v) > 0 from Vendita v where v.piattaforma.id = :id")
	boolean haVendite(@Param("id") Long id);

	/** Coppie [piattaformaId, numero vendite]. */
	@Query("select v.piattaforma.id, count(v) from Vendita v group by v.piattaforma.id")
	List<Object[]> contaVenditeRighe();

	default Map<Long, Long> contaVendite() {
		return contaVenditeRighe().stream()
			.collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
	}

}
