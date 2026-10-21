package it.compravendita.gestionale.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class SchemaAttributiTest {

	private final SchemaAttributi schema = new SchemaAttributi();

	@Test
	void accessorioRichiedeIlTipo() {
		SchemaAttributi.Risultato risultato = schema.valida(Categoria.ACCESSORIO, Map.of());

		assertThat(risultato.valido()).isFalse();
		assertThat(risultato.errori()).containsOnlyKeys(AttributiArticolo.TIPO_ACCESSORIO);
	}

	@Test
	void tazzaSenzaAttributiValida() {
		assertThat(schema.valida(Categoria.TAZZA, Map.of()).valido()).isTrue();
	}

	@Test
	void convertiValoriEScartaCampiVuotiOSconosciuti() {
		SchemaAttributi.Risultato risultato = schema.valida(Categoria.TAZZA,
				Map.of("materiale", "Ceramica", "capacita", "350", "colore", " Bianca ", "soggetto", "", "pippo", "x"));

		assertThat(risultato.valido()).isTrue();
		assertThat(risultato.valori()).containsOnlyKeys("materiale", "capacita", "colore");
		assertThat(risultato.valori().get("capacita")).isEqualTo(350);
		assertThat(risultato.valori().get("colore")).isEqualTo("Bianca");
	}

	@Test
	void capacitaFuoriScalaENonIntera() {
		assertThat(schema.valida(Categoria.TAZZA, Map.of("capacita", "9000")).errori()).containsKey("capacita");
		assertThat(schema.valida(Categoria.BICCHIERE, Map.of("capacita", "250,5")).errori())
			.containsEntry("capacita", "Inserisci un numero intero");
	}

	@Test
	void sceltaNonPrevistaRifiutata() {
		assertThat(schema.valida(Categoria.TAZZA, Map.of("materiale", "Legno pregiato")).errori())
			.containsEntry("materiale", "Valore non ammesso");
		assertThat(schema.valida(Categoria.TAZZA_TERMICA, Map.of("forma", "Classica")).valori())
			.as("la forma è prevista solo per le tazze")
			.isEmpty();
	}

	@Test
	void setRegaloConNumeroPezzi() {
		assertThat(schema.valida(Categoria.SET_REGALO, Map.of("numeroPezzi", "3", "contenuto", "Tazza e piattino"))
			.valori()).containsEntry("numeroPezzi", 3).containsEntry("contenuto", "Tazza e piattino");
	}

	@Test
	void riepilogoPerLista() {
		Articolo tazza = new Articolo("Tazza Stitch", Categoria.TAZZA, null);
		tazza.setAttributi(Map.of("materiale", "Ceramica", "capacita", 450, "colore", "Azzurra"));

		assertThat(schema.riepilogo(tazza)).isEqualTo("Ceramica · 450 · Azzurra");
		assertThat(schema.valoriPerForm(tazza.getAttributi())).containsEntry("capacita", "450");
	}

}
