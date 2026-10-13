package it.compravendita.gestionale.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Con il profilo {@code demo} carica i dati di esempio all'avvio, se il database è vuoto.
 * Il profilo usa lo schema PostgreSQL "demo" (vedi application-demo.yml): i dati reali in
 * "public" non vengono toccati.
 */
@Component
@Profile("demo")
class CaricamentoDemo implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CaricamentoDemo.class);

	private final DatiDemo datiDemo;

	private final JdbcTemplate jdbcTemplate;

	CaricamentoDemo(DatiDemo datiDemo, JdbcTemplate jdbcTemplate) {
		this.datiDemo = datiDemo;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		// sicurezza: mai dati finti nello schema dei dati reali
		String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
		if (!"demo".equals(schema)) {
			log.warn("Dati demo non caricati: la connessione usa lo schema \"{}\" invece di \"demo\"", schema);
			return;
		}
		datiDemo.caricaSeVuoto();
	}

}
