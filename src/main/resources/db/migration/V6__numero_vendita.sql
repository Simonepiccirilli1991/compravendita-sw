-- Numero progressivo della vendita, assegnato dal database (non più inserito a mano).
-- Le vendite esistenti prendono il proprio id, così i numeri già usati restano gli stessi.
create sequence vendita_numero_seq;

alter table vendita add column numero bigint;
update vendita set numero = id;
select setval('vendita_numero_seq', coalesce((select max(numero) from vendita), 0) + 1, false);

alter table vendita
    alter column numero set default nextval('vendita_numero_seq'),
    alter column numero set not null,
    add constraint uk_vendita_numero unique (numero);
alter sequence vendita_numero_seq owned by vendita.numero;

-- il vecchio riferimento inserito a mano resta nella colonna riferimento_ordine, non più usata
