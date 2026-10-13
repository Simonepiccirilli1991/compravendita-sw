package it.compravendita.gestionale.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class RisorsaNonTrovataException extends RuntimeException {

	public RisorsaNonTrovataException(String risorsa, Long id) {
		super(risorsa + " " + id + " non trovato");
	}

}
