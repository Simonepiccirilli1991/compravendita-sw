package it.compravendita.gestionale.common;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Entità con data di creazione e ultimo aggiornamento gestite da Hibernate.
 */
@MappedSuperclass
public abstract class AuditedEntity extends AbstractEntity {

	@CreationTimestamp
	@Column(name = "creato_il", nullable = false, updatable = false)
	private Instant creatoIl;

	@UpdateTimestamp
	@Column(name = "aggiornato_il", nullable = false)
	private Instant aggiornatoIl;

	public Instant getCreatoIl() {
		return creatoIl;
	}

	public Instant getAggiornatoIl() {
		return aggiornatoIl;
	}

}
