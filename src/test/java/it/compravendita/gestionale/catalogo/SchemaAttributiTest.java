package it.compravendita.gestionale.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SchemaAttributiTest {

	private final SchemaAttributi schema = new SchemaAttributi();

	@Test
	void cartaGradataRichiedeEnteVotoECertificato() {
		SchemaAttributi.Risultato risultato = schema.valida(Categoria.CARTA_GRADATA, Map.of());

		assertThat(risultato.valido()).isFalse();
		assertThat(risultato.errori()).containsOnlyKeys(AttributiArticolo.ENTE_GRADING, AttributiArticolo.VOTO,
				AttributiArticolo.NUMERO_CERTIFICATO);
	}

	@Test
	void convertiValoriEScartaCampiVuotiOSconosciuti() {
		SchemaAttributi.Risultato risultato = schema.valida(Categoria.CARTA_GRADATA,
				Map.of("enteGrading", "PSA", "voto", "9,5", "numeroCertificato", " 123 ", "set", "", "pippo", "x"));

		assertThat(risultato.valido()).isTrue();
		assertThat(risultato.valori()).containsOnlyKeys("enteGrading", "voto", "numeroCertificato");
		assertThat(risultato.valori().get("voto")).isEqualTo(new BigDecimal("9.5"));
		assertThat(risultato.valori().get("numeroCertificato")).isEqualTo("123");
	}

	@Test
	void votoFuoriScalaENonNumerico() {
		assertThat(schema.valida(Categoria.CARTA_GRADATA,
				Map.of("enteGrading", "PSA", "voto", "11", "numeroCertificato", "1")).errori())
			.containsKey("voto");
		assertThat(schema.valida(Categoria.CARTA_GRADATA,
				Map.of("enteGrading", "PSA", "voto", "dieci", "numeroCertificato", "1")).errori())
			.containsEntry("voto", "Inserisci un numero");
	}

	@Test
	void sceltaNonPrevistaRifiutata() {
		assertThat(schema.valida(Categoria.VIDEOGIOCO, Map.of("console", "SNES", "regione", "EU")).errori())
			.containsEntry("regione", "Valore non ammesso");
	}

	@Test
	void tazzaConCapacitaIntera() {
		assertThat(schema.valida(Categoria.TAZZA, Map.of("materiale", "Gres", "capacita", "400")).valori())
			.containsEntry("materiale", "Gres")
			.containsEntry("capacita", 400);
		assertThat(schema.valida(Categoria.TAZZA, Map.of("materiale", "Legno")).errori())
			.containsEntry("materiale", "Valore non ammesso");
		assertThat(schema.valida(Categoria.OGGETTISTICA, Map.of()).valido()).isTrue();
	}

	@Test
	void legoAnnoInteroESiNoVideogioco() {
		assertThat(schema.valida(Categoria.LEGO, Map.of("numeroSet", "75192", "anno", "2017")).valori())
			.containsEntry("anno", 2017);
		assertThat(schema.valida(Categoria.LEGO, Map.of("numeroSet", "75192", "anno", "2017.5")).errori())
			.containsKey("anno");
		assertThat(schema.valida(Categoria.VIDEOGIOCO, Map.of("console", "SNES", "retro", "true")).valori())
			.containsEntry("retro", true);
	}

	@Test
	void riepilogoPerLista() {
		Articolo carta = new Articolo("Charizard", Categoria.CARTA_GRADATA, null);
		carta.setAttributi(Map.of("enteGrading", "PSA", "voto", 10.0, "numeroCertificato", "999"));

		assertThat(schema.riepilogo(carta)).isEqualTo("PSA · 10 · 999");
		assertThat(schema.valoriPerForm(carta.getAttributi())).containsEntry("voto", "10");
	}

}
