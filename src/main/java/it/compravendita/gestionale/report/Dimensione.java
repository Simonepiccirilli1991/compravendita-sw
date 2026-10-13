package it.compravendita.gestionale.report;

/**
 * Voce per cui raggruppare i risultati delle vendite. Contiene i frammenti SQL usati da
 * {@link CalcoloEconomicoService} sulla tabella temporanea {@code righe}.
 */
public enum Dimensione {

	CATEGORIA("Categoria", "r.categoria", "r.categoria", ""),
	BRAND("Brand", "cast(r.brand_id as varchar)", "coalesce(b.nome, 'Senza brand')",
			" left join brand b on b.id = r.brand_id"),
	PIATTAFORMA("Piattaforma", "cast(r.piattaforma_id as varchar)", "p.nome",
			" join piattaforma p on p.id = r.piattaforma_id"),
	ARTICOLO("Articolo", "cast(r.articolo_id as varchar)", "r.articolo_nome", ""),
	MESE("Mese", "to_char(r.data_vendita, 'YYYY-MM')", "to_char(r.data_vendita, 'YYYY-MM')", "");

	private final String etichetta;

	private final String chiaveSql;

	private final String etichettaSql;

	private final String joinSql;

	Dimensione(String etichetta, String chiaveSql, String etichettaSql, String joinSql) {
		this.etichetta = etichetta;
		this.chiaveSql = chiaveSql;
		this.etichettaSql = etichettaSql;
		this.joinSql = joinSql;
	}

	public String getEtichetta() {
		return etichetta;
	}

	String chiaveSql() {
		return chiaveSql;
	}

	String etichettaSql() {
		return etichettaSql;
	}

	String joinSql() {
		return joinSql;
	}

}
