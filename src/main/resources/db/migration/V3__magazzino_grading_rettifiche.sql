-- =====================================================================
-- Fase 3: magazzino, grading e rettifiche inventario
-- =====================================================================

-- Nuovo stato per i lotti azzerati da una rettifica (perso, danneggiato, uso personale)
alter table riga_acquisto drop constraint ck_riga_acquisto_stato;
alter table riga_acquisto add constraint ck_riga_acquisto_stato check (stato in
    ('IN_ARRIVO', 'IN_STOCK', 'IN_GRADING', 'IN_VENDITA', 'VENDUTO', 'RESO', 'SCARICATO'));

create index ix_riga_acquisto_stato on riga_acquisto (stato);

-- ---------------------------------------------------------------------
-- Rettifiche inventario: scarico di pezzi da un lotto senza vendita
-- ---------------------------------------------------------------------
create table rettifica_magazzino (
    id                 bigserial primary key,
    riga_acquisto_id   bigint         not null references riga_acquisto (id),
    data_rettifica     date           not null,
    quantita           integer        not null,
    motivo             varchar(30)    not null,
    -- costo unitario del lotto al momento della rettifica
    costo_unitario     numeric(14, 4) not null,
    -- eventuale rimborso (reso al fornitore, assicurazione spedizione)
    importo_recuperato numeric(12, 2) not null default 0,
    note               text,
    creato_il          timestamptz    not null default now(),
    aggiornato_il      timestamptz    not null default now(),
    constraint ck_rettifica_quantita check (quantita > 0),
    constraint ck_rettifica_importi check (costo_unitario >= 0 and importo_recuperato >= 0),
    constraint ck_rettifica_motivo check (motivo in
        ('PERSO', 'DANNEGGIATO', 'USO_PERSONALE', 'RESO_FORNITORE', 'CORREZIONE'))
);

create index ix_rettifica_riga_acquisto on rettifica_magazzino (riga_acquisto_id);
create index ix_rettifica_data on rettifica_magazzino (data_rettifica);

-- ---------------------------------------------------------------------
-- Grading: invio di una carta a un ente e rientro come carta gradata.
-- riga_acquisto_id è il lotto (di 1 pezzo) che rappresenta la carta inviata.
-- ---------------------------------------------------------------------
create table grading (
    id                    bigserial primary key,
    riga_acquisto_id      bigint         not null references riga_acquisto (id),
    articolo_originale_id bigint         not null references articolo (id),
    ente                  varchar(30)    not null,
    servizio              varchar(100),
    numero_ordine         varchar(100),
    data_invio            date           not null,
    data_rientro          date,
    costo                 numeric(12, 2) not null default 0,
    voto                  numeric(4, 1),
    numero_certificato    varchar(100),
    stato                 varchar(20)    not null default 'INVIATO',
    note                  text,
    creato_il             timestamptz    not null default now(),
    aggiornato_il         timestamptz    not null default now(),
    constraint ck_grading_costo check (costo >= 0),
    constraint ck_grading_voto check (voto is null or (voto >= 1 and voto <= 10)),
    constraint ck_grading_stato check (stato in ('INVIATO', 'RIENTRATO', 'ANNULLATO'))
);

create index ix_grading_riga_acquisto on grading (riga_acquisto_id);
create index ix_grading_stato on grading (stato);
