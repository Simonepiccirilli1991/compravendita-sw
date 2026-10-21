package it.compravendita.gestionale.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.compravendita.gestionale.TestcontainersConfiguration;
import it.compravendita.gestionale.acquisti.Acquisto;
import it.compravendita.gestionale.acquisti.AcquistoRepository;
import it.compravendita.gestionale.acquisti.RigaAcquisto;
import it.compravendita.gestionale.catalogo.Articolo;
import it.compravendita.gestionale.catalogo.ArticoloRepository;
import it.compravendita.gestionale.catalogo.BrandRepository;
import it.compravendita.gestionale.catalogo.Categoria;
import it.compravendita.gestionale.magazzino.MotivoRettifica;
import it.compravendita.gestionale.magazzino.RettificaMagazzino;
import it.compravendita.gestionale.magazzino.RettificaMagazzinoRepository;
import it.compravendita.gestionale.piattaforme.Piattaforma;
import it.compravendita.gestionale.piattaforme.PiattaformaRepository;
import it.compravendita.gestionale.spese.CategoriaSpesa;
import it.compravendita.gestionale.spese.Spesa;
import it.compravendita.gestionale.spese.SpesaRepository;
import it.compravendita.gestionale.vendite.RigaVendita;
import it.compravendita.gestionale.vendite.Vendita;
import it.compravendita.gestionale.vendite.VenditaRepository;
import jakarta.persistence.EntityManager;

/**
 * Scenario completo con risultati calcolati a mano:
 * <ul>
 * <li>Acquisto A (gen): 4 carte a 10 + 1 LEGO a 100, spedizione 14 ripartita sul prezzo → carte a 11, LEGO a 110</li>
 * <li>Acquisto B (feb): 2 carte a 15 + 1 omaggio a 0</li>
 * <li>V1 (mar, eBay): 5 carte a 30 (4 dal lotto A, 1 dal B) + LEGO a 200; commissioni 35, spedizione 15 → netto 300</li>
 * <li>V2 (mar, Vinted): omaggio a 20, netto 20, costo zero</li>
 * <li>V3 (apr, eBay): 1 carta a 40 dal lotto B, poi resa con 6 di costi a carico</li>
 * <li>V4 (apr, eBay): 1 carta a 50, annullata</li>
 * <li>Rettifica (apr): 1 carta del lotto B persa (15); spesa generale (mar) 9,99</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser
class CalcoloEconomicoTest {

	@Autowired
	CalcoloEconomicoService calcolo;

	@Autowired
	MockMvc mvc;

	@Autowired
	ArticoloRepository articoloRepository;

	@Autowired
	BrandRepository brandRepository;

	@Autowired
	AcquistoRepository acquistoRepository;

	@Autowired
	PiattaformaRepository piattaformaRepository;

	@Autowired
	VenditaRepository venditaRepository;

	@Autowired
	RettificaMagazzinoRepository rettificaRepository;

	@Autowired
	SpesaRepository spesaRepository;

	@Autowired
	EntityManager em;

	Acquisto acquistoA;

	Acquisto acquistoB;

	RigaAcquisto carteA;

	RigaAcquisto legoA;

	@BeforeEach
	void scenario() {
		Articolo carta = articoloRepository.save(new Articolo("Mewtwo", Categoria.TAZZA,
				brandRepository.findByNomeIgnoreCase("Pokémon").orElseThrow()));
		Articolo lego = articoloRepository.save(new Articolo("Colosseo", Categoria.TAZZA_TERMICA,
				brandRepository.findByNomeIgnoreCase("Disney").orElseThrow()));
		Articolo omaggio = articoloRepository.save(new Articolo("Bustine vuote", Categoria.ALTRO, null));
		Piattaforma ebay = piattaformaRepository.findByNomeIgnoreCase("eBay").orElseThrow();
		Piattaforma vinted = piattaformaRepository.findByNomeIgnoreCase("Vinted").orElseThrow();

		acquistoA = new Acquisto(LocalDate.of(2026, 1, 10));
		carteA = new RigaAcquisto(carta, 4, new BigDecimal("10"));
		legoA = new RigaAcquisto(lego, 1, new BigDecimal("100"));
		acquistoA.aggiungiRiga(carteA);
		acquistoA.aggiungiRiga(legoA);
		acquistoA.setCostoSpedizione(new BigDecimal("14"));
		acquistoA.ripartisciCostiAccessori();
		acquistoB = new Acquisto(LocalDate.of(2026, 2, 10));
		RigaAcquisto carteB = new RigaAcquisto(carta, 2, new BigDecimal("15"));
		RigaAcquisto omaggioB = new RigaAcquisto(omaggio, 1, BigDecimal.ZERO);
		acquistoB.aggiungiRiga(carteB);
		acquistoB.aggiungiRiga(omaggioB);
		acquistoRepository.saveAll(List.of(acquistoA, acquistoB));

		Vendita v1 = new Vendita(LocalDate.of(2026, 3, 1), ebay);
		RigaVendita carteV1 = new RigaVendita(carta, 5, new BigDecimal("30"));
		carteV1.allocaDa(carteA, 4);
		carteV1.allocaDa(carteB, 1);
		RigaVendita legoV1 = new RigaVendita(lego, 1, new BigDecimal("200"));
		legoV1.allocaDa(legoA, 1);
		v1.aggiungiRiga(carteV1);
		v1.aggiungiRiga(legoV1);
		v1.setCommissioniPiattaforma(new BigDecimal("35"));
		v1.setCostoSpedizione(new BigDecimal("15"));

		Vendita v2 = new Vendita(LocalDate.of(2026, 3, 15), vinted);
		RigaVendita omaggioV2 = new RigaVendita(omaggio, 1, new BigDecimal("20"));
		omaggioV2.allocaDa(omaggioB, 1);
		v2.aggiungiRiga(omaggioV2);

		Vendita v3 = new Vendita(LocalDate.of(2026, 4, 1), ebay);
		RigaVendita cartaV3 = new RigaVendita(carta, 1, new BigDecimal("40"));
		cartaV3.allocaDa(carteB, 1);
		v3.aggiungiRiga(cartaV3);
		v3.registraReso(LocalDate.of(2026, 4, 8), new BigDecimal("6"), "Rovinata");

		Vendita v4 = new Vendita(LocalDate.of(2026, 4, 5), ebay);
		RigaVendita cartaV4 = new RigaVendita(carta, 1, new BigDecimal("50"));
		cartaV4.allocaDa(carteB, 1);
		v4.aggiungiRiga(cartaV4);
		v4.annulla(LocalDate.of(2026, 4, 6), null);
		venditaRepository.saveAll(List.of(v1, v2, v3, v4));

		rettificaRepository.save(new RettificaMagazzino(carteB, LocalDate.of(2026, 4, 10), 1, MotivoRettifica.PERSO,
				null, null));
		spesaRepository.save(new Spesa(LocalDate.of(2026, 3, 20), CategoriaSpesa.ABBONAMENTO, "Abbonamento",
				new BigDecimal("9.99")));
		em.flush();
	}

	@Test
	void costiUnitariDelloScenario() {
		assertThat(carteA.getCostoUnitarioEffettivo()).isEqualByComparingTo("11");
		assertThat(legoA.getCostoUnitarioEffettivo()).isEqualByComparingTo("110");
	}

	@Test
	void totaleEscludeAnnullateEContaIResi() {
		Risultato totale = calcolo.totale(Periodo.sempre());

		assertThat(totale.vendite()).isEqualTo(2);
		assertThat(totale.pezzi()).isEqualTo(7);
		assertThat(totale.lordo()).isEqualByComparingTo("370");
		assertThat(totale.netto()).isEqualByComparingTo("320");
		// 4 x 11 + 1 x 15 + 110 + 0
		assertThat(totale.costoVenduto()).isEqualByComparingTo("169");
		assertThat(totale.costiResi()).isEqualByComparingTo("6");
		assertThat(totale.profitto()).isEqualByComparingTo("145");
		assertThat(totale.roi()).isEqualByComparingTo("85.8");
		assertThat(totale.margine()).isEqualByComparingTo("39.2");
	}

	@Test
	void riepilogoConPerditeSpeseEAcquisti() {
		RiepilogoPeriodo riepilogo = calcolo.riepilogo(Periodo.sempre());

		assertThat(riepilogo.perditeRettifiche()).isEqualByComparingTo("15");
		assertThat(riepilogo.speseGenerali()).isEqualByComparingTo("9.99");
		assertThat(riepilogo.acquistato()).isEqualByComparingTo("184");
		assertThat(riepilogo.profittoNetto()).isEqualByComparingTo("120.01");
	}

	@Test
	void venditaConPiuArticoliRipartitaPerCategoria() {
		List<Risultato> categorie = calcolo.per(Dimensione.CATEGORIA, Periodo.sempre());

		assertThat(categorie).extracting(Risultato::chiave).containsExactly("TAZZA", "TAZZA_TERMICA", "ALTRO");
		Risultato carte = categorie.get(0);
		assertThat(carte.etichetta()).isEqualTo("Tazza");
		// netto 300 ripartito 150/350 alle carte e 200/350 al LEGO
		assertThat(carte.netto()).isEqualByComparingTo("128.57");
		assertThat(carte.costoVenduto()).isEqualByComparingTo("59");
		assertThat(carte.costiResi()).isEqualByComparingTo("6");
		assertThat(carte.profitto()).isEqualByComparingTo("63.57");
		Risultato lego = categorie.get(1);
		assertThat(lego.netto()).isEqualByComparingTo("171.43");
		assertThat(lego.profitto()).isEqualByComparingTo("61.43");
		// omaggio: costo zero, ROI non definito
		Risultato altro = categorie.get(2);
		assertThat(altro.profitto()).isEqualByComparingTo("20");
		assertThat(altro.roi()).isNull();
		assertThat(altro.margine()).isEqualByComparingTo("100");
	}

	@Test
	void perBrandPiattaformaArticoloEMese() {
		assertThat(calcolo.per(Dimensione.BRAND, Periodo.sempre()))
			.extracting(Risultato::etichetta)
			.containsExactly("Pokémon", "Disney", "Senza brand");

		List<Risultato> piattaforme = calcolo.per(Dimensione.PIATTAFORMA, Periodo.sempre());
		assertThat(piattaforme).extracting(Risultato::etichetta).containsExactly("eBay", "Vinted");
		assertThat(piattaforme.get(0).profitto()).isEqualByComparingTo("125");
		assertThat(piattaforme.get(0).vendite()).isEqualTo(1);

		assertThat(calcolo.per(Dimensione.ARTICOLO, Periodo.sempre()))
			.extracting(Risultato::etichetta)
			.containsExactly("Mewtwo", "Colosseo", "Bustine vuote");

		List<Risultato> mesi = calcolo.per(Dimensione.MESE, Periodo.sempre());
		assertThat(mesi).extracting(Risultato::chiave).containsExactly("2026-03", "2026-04");
		assertThat(mesi.get(0).profitto()).isEqualByComparingTo("151");
		assertThat(mesi.get(1).vendite()).isZero();
		assertThat(mesi.get(1).profitto()).isEqualByComparingTo("-6");
	}

	@Test
	void filtroPerPeriodo() {
		Periodo marzo = new Periodo(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

		Risultato totale = calcolo.totale(marzo);
		assertThat(totale.vendite()).isEqualTo(2);
		assertThat(totale.costiResi()).isEqualByComparingTo("0");
		assertThat(totale.profitto()).isEqualByComparingTo("151");

		RiepilogoPeriodo riepilogo = calcolo.riepilogo(marzo);
		assertThat(riepilogo.perditeRettifiche()).isEqualByComparingTo("0");
		assertThat(riepilogo.acquistato()).isEqualByComparingTo("0");

		assertThat(calcolo.totale(new Periodo(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))).vendite()).isZero();
	}

	@Test
	void risultatoPerAcquistoConQuantitaParziali() {
		RisultatoAcquisto a = calcolo.perAcquisto(acquistoA);
		assertThat(a.investito()).isEqualByComparingTo("154");
		assertThat(a.pezziVenduti()).isEqualTo(5);
		assertThat(a.pezziResidui()).isZero();
		// carte: 4 dei 5 pezzi della riga di V1 → 128,57 x 4/5
		assertThat(a.righe().get(carteA.getId()).ricavoNetto()).isEqualByComparingTo("102.86");
		assertThat(a.righe().get(carteA.getId()).costoVenduto()).isEqualByComparingTo("44");
		assertThat(a.ricavoNetto()).isEqualByComparingTo("274.29");
		assertThat(a.profittoRealizzato()).isEqualByComparingTo("120.29");
		assertThat(a.isRipagato()).isTrue();
		assertThat(a.recupero()).isEqualByComparingTo("178.1");

		RisultatoAcquisto b = calcolo.perAcquisto(acquistoB);
		assertThat(b.investito()).isEqualByComparingTo("30");
		// 1 carta in V1 e l'omaggio in V2; la vendita resa e quella annullata non contano
		assertThat(b.pezziVenduti()).isEqualTo(2);
		assertThat(b.ricavoNetto()).isEqualByComparingTo("45.71");
		assertThat(b.costoVenduto()).isEqualByComparingTo("15");
		assertThat(b.perditeRettifiche()).isEqualByComparingTo("15");
		assertThat(b.risultato()).isEqualByComparingTo("15.71");
		// la carta rientrata dal reso è poi andata persa
		assertThat(b.pezziResidui()).isZero();
		assertThat(b.isRipagato()).isTrue();
	}

	@Test
	void pagineReport() throws Exception {
		mvc.perform(get("/report").param("per", "PIATTAFORMA"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("eBay")))
			.andExpect(content().string(containsString("120,01")));
		mvc.perform(get("/report").param("per", "MESE").param("da", "2026-04-01").param("a", "2026-04-30"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("2026-04")));
		mvc.perform(get("/report/acquisti/{id}", acquistoA.getId()).header("HX-Request", "true"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Investimento rientrato")))
			.andExpect(content().string(containsString("274,29")));
	}

}
