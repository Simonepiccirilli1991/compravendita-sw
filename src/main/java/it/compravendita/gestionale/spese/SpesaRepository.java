package it.compravendita.gestionale.spese;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpesaRepository extends JpaRepository<Spesa, Long> {

	List<Spesa> findByDataSpesaBetweenOrderByDataSpesaDesc(LocalDate da, LocalDate a);

	boolean existsByArticoloId(Long articoloId);

}
