-- =====================================================================
-- Schema iniziale gestionale compravendita
-- Importi: numeric(12,2); costi unitari calcolati: numeric(14,4)
-- =====================================================================

-- ---------------------------------------------------------------------
-- Catalogo
-- ---------------------------------------------------------------------
create table brand (
    id            bigserial primary key,
    nome          varchar(100) not null,
    attivo        boolean      not null default true,
    creato_il     timestamptz  not null default now(),
    aggiornato_il timestamptz  not null default now(),
    constraint uk_brand_nome unique (nome)
);

create table articolo (
    id            bigserial primary key,
    nome          varchar(255) not null,
    categoria     varchar(30)  not null,
    brand_id      bigint       references brand (id),
    lingua        varchar(30),
    condizione    varchar(30),
    note          text,
    foto_path     varchar(500),
    -- attributi specifici per categoria (set, numero carta, grading, console, set LEGO, ...)
    attributi     jsonb        not null default '{}'::jsonb,
    creato_il     timestamptz  not null default now(),
    aggiornato_il timestamptz  not null default now(),
    constraint ck_articolo_categoria check (categoria in
        ('CARTA_SINGOLA', 'CARTA_GRADATA', 'SEALED_TCG', 'VIDEOGIOCO', 'LEGO', 'ALTRO')),
    constraint ck_articolo_condizione check (condizione is null or condizione in
        ('SIGILLATO', 'MINT', 'NEAR_MINT', 'EXCELLENT', 'GOOD', 'LIGHT_PLAYED', 'PLAYED', 'POOR'))
);

create index ix_articolo_categoria on articolo (categoria);
create index ix_articolo_brand on articolo (brand_id);
create index ix_articolo_nome on articolo (lower(nome));
create index ix_articolo_attributi on articolo using gin (attributi);

-- ---------------------------------------------------------------------
-- Piattaforme di vendita
-- ---------------------------------------------------------------------
create table piattaforma (
    id                        bigserial primary key,
    nome                      varchar(100)  not null,
    fee_percentuale           numeric(5, 2) not null default 0,
    fee_fissa                 numeric(12, 2) not null default 0,
    fee_pagamento_percentuale numeric(5, 2) not null default 0,
    fee_pagamento_fissa       numeric(12, 2) not null default 0,
    attiva                    boolean       not null default true,
    note                      text,
    creato_il                 timestamptz   not null default now(),
    aggiornato_il             timestamptz   not null default now(),
    constraint uk_piattaforma_nome unique (nome),
    constraint ck_piattaforma_fee check (fee_percentuale >= 0 and fee_fissa >= 0
        and fee_pagamento_percentuale >= 0 and fee_pagamento_fissa >= 0)
);

-- ---------------------------------------------------------------------
-- Acquisti
-- ---------------------------------------------------------------------
create table acquisto (
    id                   bigserial primary key,
    data_acquisto        date           not null,
    fornitore            varchar(255),
    canale               varchar(100),
    costo_spedizione     numeric(12, 2) not null default 0,
    commissioni          numeric(12, 2) not null default 0,
    altri_costi          numeric(12, 2) not null default 0,
    metodo_ripartizione  varchar(20)    not null default 'PREZZO',
    note                 text,
    allegato_path        varchar(500),
    creato_il            timestamptz    not null default now(),
    aggiornato_il        timestamptz    not null default now(),
    constraint ck_acquisto_costi check (costo_spedizione >= 0 and commissioni >= 0 and altri_costi >= 0),
    constraint ck_acquisto_ripartizione check (metodo_ripartizione in ('PREZZO', 'QUANTITA'))
);

create index ix_acquisto_data on acquisto (data_acquisto);

create table riga_acquisto (
    id                        bigserial primary key,
    acquisto_id               bigint         not null references acquisto (id) on delete cascade,
    articolo_id               bigint         not null references articolo (id),
    quantita                  integer        not null,
    prezzo_unitario           numeric(12, 2) not null,
    -- quota dei costi accessori dell'acquisto attribuita alla riga (totale riga)
    quota_costi_accessori     numeric(12, 2) not null default 0,
    -- costi sostenuti dopo l'acquisto (es. grading), totale riga
    costi_aggiuntivi          numeric(12, 2) not null default 0,
    -- (prezzo_unitario * quantita + quota_costi_accessori + costi_aggiuntivi) / quantita
    costo_unitario_effettivo  numeric(14, 4) not null,
    quantita_residua          integer        not null,
    stato                     varchar(20)    not null default 'IN_STOCK',
    creato_il                 timestamptz    not null default now(),
    aggiornato_il             timestamptz    not null default now(),
    constraint ck_riga_acquisto_quantita check (quantita > 0 and quantita_residua >= 0 and quantita_residua <= quantita),
    constraint ck_riga_acquisto_importi check (prezzo_unitario >= 0 and quota_costi_accessori >= 0
        and costi_aggiuntivi >= 0 and costo_unitario_effettivo >= 0),
    constraint ck_riga_acquisto_stato check (stato in
        ('IN_ARRIVO', 'IN_STOCK', 'IN_GRADING', 'IN_VENDITA', 'VENDUTO', 'RESO'))
);

create index ix_riga_acquisto_acquisto on riga_acquisto (acquisto_id);
create index ix_riga_acquisto_articolo on riga_acquisto (articolo_id);
-- ricerca dei lotti disponibili per l'allocazione FIFO
create index ix_riga_acquisto_disponibile on riga_acquisto (articolo_id, id) where quantita_residua > 0;

-- ---------------------------------------------------------------------
-- Vendite
-- ---------------------------------------------------------------------
create table vendita (
    id                      bigserial primary key,
    data_vendita            date           not null,
    piattaforma_id          bigint         not null references piattaforma (id),
    acquirente              varchar(255),
    riferimento_ordine      varchar(100),
    -- somma di prezzo_unitario * quantita delle righe
    totale_lordo            numeric(12, 2) not null default 0,
    spedizione_incassata    numeric(12, 2) not null default 0,
    commissioni_piattaforma numeric(12, 2) not null default 0,
    commissioni_pagamento   numeric(12, 2) not null default 0,
    costo_spedizione        numeric(12, 2) not null default 0,
    costo_imballaggio       numeric(12, 2) not null default 0,
    altri_costi             numeric(12, 2) not null default 0,
    -- lordo + spedizione incassata - commissioni - costi di spedizione/imballaggio/altri
    netto                   numeric(12, 2) not null default 0,
    stato                   varchar(20)    not null default 'COMPLETATA',
    note                    text,
    creato_il               timestamptz    not null default now(),
    aggiornato_il           timestamptz    not null default now(),
    constraint ck_vendita_importi check (totale_lordo >= 0 and spedizione_incassata >= 0
        and commissioni_piattaforma >= 0 and commissioni_pagamento >= 0
        and costo_spedizione >= 0 and costo_imballaggio >= 0 and altri_costi >= 0),
    constraint ck_vendita_stato check (stato in ('COMPLETATA', 'ANNULLATA', 'RESA'))
);

create index ix_vendita_data on vendita (data_vendita);
create index ix_vendita_piattaforma on vendita (piattaforma_id);
create index ix_vendita_stato_data on vendita (stato, data_vendita);

create table riga_vendita (
    id              bigserial primary key,
    vendita_id      bigint         not null references vendita (id) on delete cascade,
    articolo_id     bigint         not null references articolo (id),
    quantita        integer        not null,
    prezzo_unitario numeric(12, 2) not null,
    constraint ck_riga_vendita_quantita check (quantita > 0),
    constraint ck_riga_vendita_prezzo check (prezzo_unitario >= 0)
);

create index ix_riga_vendita_vendita on riga_vendita (vendita_id);
create index ix_riga_vendita_articolo on riga_vendita (articolo_id);

-- Collega ogni riga di vendita ai lotti d'acquisto da cui è stata prelevata (FIFO)
create table allocazione_vendita (
    id               bigserial primary key,
    riga_vendita_id  bigint         not null references riga_vendita (id) on delete cascade,
    riga_acquisto_id bigint         not null references riga_acquisto (id),
    quantita         integer        not null,
    -- costo unitario del lotto al momento della vendita
    costo_unitario   numeric(14, 4) not null,
    constraint ck_allocazione_quantita check (quantita > 0),
    constraint ck_allocazione_costo check (costo_unitario >= 0)
);

create index ix_allocazione_riga_vendita on allocazione_vendita (riga_vendita_id);
create index ix_allocazione_riga_acquisto on allocazione_vendita (riga_acquisto_id);

-- ---------------------------------------------------------------------
-- Spese generiche (grading, materiali, abbonamenti, ...)
-- ---------------------------------------------------------------------
create table spesa (
    id               bigserial primary key,
    data_spesa       date           not null,
    categoria        varchar(30)    not null,
    descrizione      varchar(255)   not null,
    importo          numeric(12, 2) not null,
    articolo_id      bigint         references articolo (id),
    riga_acquisto_id bigint         references riga_acquisto (id),
    note             text,
    creato_il        timestamptz    not null default now(),
    aggiornato_il    timestamptz    not null default now(),
    constraint ck_spesa_importo check (importo >= 0),
    constraint ck_spesa_categoria check (categoria in
        ('GRADING', 'MATERIALI', 'SPEDIZIONE', 'ABBONAMENTO', 'FIERA', 'ALTRO'))
);

create index ix_spesa_data on spesa (data_spesa);
create index ix_spesa_articolo on spesa (articolo_id);
create index ix_spesa_riga_acquisto on spesa (riga_acquisto_id);
