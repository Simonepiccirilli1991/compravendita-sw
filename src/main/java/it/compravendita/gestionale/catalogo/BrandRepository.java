package it.compravendita.gestionale.catalogo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

	Optional<Brand> findByNomeIgnoreCase(String nome);

	List<Brand> findByAttivoTrueOrderByNomeAsc();

}
