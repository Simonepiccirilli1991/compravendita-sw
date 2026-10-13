package it.compravendita.gestionale.catalogo;

import it.compravendita.gestionale.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "brand")
public class Brand extends AuditedEntity {

	@Column(nullable = false, length = 100)
	private String nome;

	@Column(nullable = false)
	private boolean attivo = true;

	protected Brand() {
	}

	public Brand(String nome) {
		this.nome = nome;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public boolean isAttivo() {
		return attivo;
	}

	public void setAttivo(boolean attivo) {
		this.attivo = attivo;
	}

}
