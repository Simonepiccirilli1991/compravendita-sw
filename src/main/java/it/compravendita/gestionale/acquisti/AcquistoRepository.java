package it.compravendita.gestionale.acquisti;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AcquistoRepository extends JpaRepository<Acquisto, Long>, JpaSpecificationExecutor<Acquisto> {

	@EntityGraph(attributePaths = { "righe", "righe.articolo" })
	Optional<Acquisto> findConRigheById(Long id);

	List<Acquisto> findByDataAcquistoBetweenOrderByDataAcquistoDesc(LocalDate da, LocalDate a);

}
