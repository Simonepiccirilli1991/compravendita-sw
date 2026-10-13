package it.compravendita.gestionale.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Trasforma le eccezioni non gestite dai controller in una pagina d'errore in italiano
 * (template {@code error.html}, lo stesso usato da Spring Boot per gli errori fuori dai
 * controller). Per le richieste HTMX restituisce solo il messaggio, che il layout mostra
 * in un avviso senza ricaricare la pagina.
 */
@ControllerAdvice
public class GestoreErrori {

	private static final Logger log = LoggerFactory.getLogger(GestoreErrori.class);

	@ExceptionHandler(RisorsaNonTrovataException.class)
	public ModelAndView nonTrovato(RisorsaNonTrovataException ex, HttpServletRequest request,
			HttpServletResponse response) {
		return errore(HttpStatus.NOT_FOUND, ex.getMessage(), request, response);
	}

	@ExceptionHandler(OperazioneNonConsentitaException.class)
	public ModelAndView nonConsentita(OperazioneNonConsentitaException ex, HttpServletRequest request,
			HttpServletResponse response) {
		return errore(HttpStatus.CONFLICT, ex.getMessage(), request, response);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ModelAndView fileTroppoGrande(HttpServletRequest request, HttpServletResponse response) {
		return errore(HttpStatus.PAYLOAD_TOO_LARGE, "Il file è troppo grande: il massimo è 10 MB", request,
				response);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ModelAndView parametroNonValido(MethodArgumentTypeMismatchException ex, HttpServletRequest request,
			HttpServletResponse response) {
		return errore(HttpStatus.BAD_REQUEST, "Valore non valido per \"" + ex.getName() + "\"", request, response);
	}

	@ExceptionHandler(Exception.class)
	public ModelAndView imprevisto(Exception ex, HttpServletRequest request, HttpServletResponse response) {
		HttpStatusCode stato = statoDi(ex);
		if (stato.is5xxServerError()) {
			log.error("Errore su {} {}", request.getMethod(), request.getRequestURI(), ex);
			return errore(stato, "Si è verificato un errore imprevisto. Riprova; se il problema continua "
					+ "controlla il log dell'applicazione.", request, response);
		}
		return errore(stato, messaggioPer(stato), request, response);
	}

	/** Messaggio generico in italiano per i principali codici HTTP. */
	public static String messaggioPer(HttpStatusCode stato) {
		return switch (stato.value()) {
			case 400 -> "La richiesta non è valida";
			case 403 -> "Non hai i permessi per questa operazione";
			case 404 -> "La pagina richiesta non esiste";
			case 405 -> "Operazione non consentita su questo indirizzo";
			case 409 -> "L'operazione non è consentita";
			case 413 -> "Il file è troppo grande";
			default -> stato.is5xxServerError() ? "Si è verificato un errore imprevisto"
					: "La richiesta non può essere completata";
		};
	}

	/** Le eccezioni di Spring (pagina inesistente, metodo HTTP errato, ...) portano già il loro stato. */
	private static HttpStatusCode statoDi(Exception ex) {
		if (ex instanceof ErrorResponse risposta) {
			return risposta.getStatusCode();
		}
		ResponseStatus annotazione = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
		return annotazione != null ? annotazione.code() : HttpStatus.INTERNAL_SERVER_ERROR;
	}

	private static ModelAndView errore(HttpStatusCode stato, String messaggio, HttpServletRequest request,
			HttpServletResponse response) {
		boolean htmx = request.getHeader("HX-Request") != null;
		ModelAndView mav = new ModelAndView(htmx ? "error :: messaggio" : "error");
		mav.setStatus(stato);
		mav.addObject("status", stato.value());
		mav.addObject("titolo", titolo(stato));
		mav.addObject("message", messaggio);
		if (htmx) {
			// il messaggio viene mostrato dal gestore htmx:responseError del layout
			response.setHeader("HX-Reswap", "none");
		}
		return mav;
	}

	private static String titolo(HttpStatusCode stato) {
		return switch (stato.value()) {
			case 404 -> "Non trovato";
			case 409 -> "Operazione non consentita";
			default -> stato.is5xxServerError() ? "Errore" : "Richiesta non valida";
		};
	}

}
