-- =====================================================================
-- Parametri per i prezzi suggeriti (regime forfettario): una sola riga.
-- Valori INPS 2026 per i commercianti dalla circolare INPS n. 14 del 9 febbraio 2026:
-- minimale 18.808 euro, aliquota 24,48%, maternità 0,62 euro al mese; riduzione del 35%
-- del regime contributivo agevolato (legge 190/2014, art. 1, commi 77-84; circolare INPS n. 35/2016).
-- Coefficiente di redditività 40% del commercio (codici ATECO 45-47), imposta sostitutiva 5%
-- per i primi cinque anni di attività.
-- =====================================================================

create table parametri_prezzo (
    id                       integer        primary key default 1,
    coefficiente_redditivita numeric(5, 2)  not null default 40,
    aliquota_imposta         numeric(5, 2)  not null default 5,
    aliquota_contributi      numeric(5, 2)  not null default 24.48,
    riduzione_contributi     numeric(5, 2)  not null default 35,
    minimale_inps            numeric(12, 2) not null default 18808,
    contributo_maternita     numeric(12, 2) not null default 7.44,
    altri_costi_fissi        numeric(12, 2) not null default 0,
    ricavi_annui_previsti    numeric(12, 2) not null default 10000,
    margine_desiderato       numeric(6, 2)  not null default 30,
    costo_imballaggio        numeric(12, 2) not null default 1,
    costo_spedizione         numeric(12, 2) not null default 0,
    piattaforma_id           bigint references piattaforma (id) on delete set null,
    aggiornato_il            timestamptz    not null default now(),
    constraint ck_parametri_prezzo_unica check (id = 1),
    constraint ck_parametri_prezzo_valori check (
        coefficiente_redditivita between 0 and 100 and aliquota_imposta between 0 and 100
        and aliquota_contributi between 0 and 100 and riduzione_contributi between 0 and 100
        and minimale_inps >= 0 and contributo_maternita >= 0 and altri_costi_fissi >= 0
        and ricavi_annui_previsti > 0 and margine_desiderato >= 0 and costo_imballaggio >= 0
        and costo_spedizione >= 0)
);

insert into parametri_prezzo (id) values (1);
