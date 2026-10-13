-- =====================================================================
-- Fase 7: ricerca negli attributi degli articoli (n. certificato, n. set, numero carta, ...)
-- =====================================================================

-- Valori degli attributi JSONB uniti in un unico testo, per le ricerche "contiene"
create function attributi_testo(attributi jsonb) returns text
    language sql immutable as
$$
select coalesce(string_agg(valore, ' '), '') from jsonb_each_text(attributi) as e(chiave, valore)
$$;
