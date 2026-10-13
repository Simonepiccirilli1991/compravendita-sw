package it.compravendita.gestionale.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Formattazioni usate nelle pagine, accessibili da Thymeleaf come {@code @formati}.
 */
@Component("formati")
public class Formati {

	public static final ZoneId FUSO = ZoneId.of("Europe/Rome");

	private static final Locale ITALIANO = Locale.ITALY;

	private static final DateTimeFormatter DATA_ORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	public String dataOra(Instant istante) {
		return istante == null ? "—" : DATA_ORA.format(istante);
	}

	public String data(LocalDate data) {
		return data == null ? "—" : DATA.format(data);
	}

	/** Importo in euro con due decimali, es. "1.234,50 €". */
	public String euro(BigDecimal importo) {
		if (importo == null) {
			return "—";
		}
		NumberFormat formato = NumberFormat.getCurrencyInstance(ITALIANO);
		return formato.format(importo);
	}

	/** Numero senza zeri decimali superflui, es. "9,5". */
	public String numero(BigDecimal valore) {
		if (valore == null) {
			return "—";
		}
		NumberFormat formato = NumberFormat.getNumberInstance(ITALIANO);
		formato.setMaximumFractionDigits(4);
		return formato.format(valore);
	}

	/** Percentuale con un decimale, es. "12,5%". */
	public String percentuale(BigDecimal valore) {
		if (valore == null) {
			return "—";
		}
		NumberFormat formato = NumberFormat.getNumberInstance(ITALIANO);
		formato.setMinimumFractionDigits(1);
		formato.setMaximumFractionDigits(1);
		return formato.format(valore) + "%";
	}

	/** Classe CSS per evidenziare un risultato positivo o negativo. */
	public String classeSegno(BigDecimal valore) {
		if (valore == null || valore.signum() == 0) {
			return "";
		}
		return valore.signum() > 0 ? "text-success" : "text-danger";
	}

	/** Giorni trascorsi da una data a oggi. */
	public long giorniDa(LocalDate data) {
		return data == null ? 0 : ChronoUnit.DAYS.between(data, LocalDate.now(FUSO));
	}

}
