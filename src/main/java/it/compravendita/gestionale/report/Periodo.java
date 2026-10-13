package it.compravendita.gestionale.report;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import it.compravendita.gestionale.common.Formati;

/**
 * Intervallo di date (estremi inclusi) per i report; un estremo mancante significa nessun limite.
 */
public class Periodo {

	static final LocalDate MINIMO = LocalDate.of(1900, 1, 1);

	static final LocalDate MASSIMO = LocalDate.of(9999, 12, 31);

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate da;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate a;

	public Periodo() {
	}

	public Periodo(LocalDate da, LocalDate a) {
		this.da = da;
		this.a = a;
	}

	public static Periodo sempre() {
		return new Periodo();
	}

	public static Periodo annoCorrente() {
		int anno = LocalDate.now(Formati.FUSO).getYear();
		return new Periodo(LocalDate.of(anno, 1, 1), LocalDate.of(anno, 12, 31));
	}

	LocalDate inizio() {
		return da != null ? da : MINIMO;
	}

	LocalDate fine() {
		return a != null ? a : MASSIMO;
	}

	public boolean isIllimitato() {
		return da == null && a == null;
	}

	public LocalDate getDa() {
		return da;
	}

	public void setDa(LocalDate da) {
		this.da = da;
	}

	public LocalDate getA() {
		return a;
	}

	public void setA(LocalDate a) {
		this.a = a;
	}

}
