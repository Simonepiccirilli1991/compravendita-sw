-- =====================================================================
-- Versione tazze: categorie, condizioni e dati iniziali per tazze e articoli simili.
-- Il grading delle carte non esiste più.
-- =====================================================================

drop table grading;

alter table riga_acquisto drop constraint ck_riga_acquisto_stato;
alter table riga_acquisto add constraint ck_riga_acquisto_stato check (stato in
    ('IN_ARRIVO', 'IN_STOCK', 'IN_VENDITA', 'VENDUTO', 'RESO', 'SCARICATO'));

alter table articolo drop constraint ck_articolo_categoria;
alter table articolo add constraint ck_articolo_categoria check (categoria in
    ('TAZZA', 'TAZZA_TERMICA', 'BICCHIERE', 'SET_REGALO', 'ACCESSORIO', 'ALTRO'));

alter table articolo drop constraint ck_articolo_condizione;
alter table articolo add constraint ck_articolo_condizione check (condizione is null or condizione in
    ('NUOVO_CONFEZIONATO', 'NUOVO', 'COME_NUOVO', 'OTTIMO', 'BUONO', 'CON_DIFETTI'));

alter table spesa drop constraint ck_spesa_categoria;
alter table spesa add constraint ck_spesa_categoria check (categoria in
    ('MATERIALI', 'SPEDIZIONE', 'PUBBLICITA', 'ABBONAMENTO', 'FIERA', 'ALTRO'));

-- Brand / licenze tipici delle tazze al posto di quelli di carte, videogiochi e LEGO
delete from brand b where not exists (select 1 from articolo a where a.brand_id = b.id);
insert into brand (nome)
select nome from (values
    ('Disney'),
    ('Harry Potter'),
    ('Marvel'),
    ('Star Wars'),
    ('Pokémon'),
    ('Hello Kitty'),
    ('Friends'),
    ('Thun'),
    ('Ikea'),
    ('Senza licenza')) as nuovi(nome)
where not exists (select 1 from brand b where lower(b.nome) = lower(nuovi.nome));

-- Marketplace: Cardmarket vende solo carte, Etsy è adatto a tazze e oggettistica
delete from piattaforma p where p.nome = 'Cardmarket' and not exists (select 1 from vendita v where v.piattaforma_id = p.id);
insert into piattaforma (nome, note)
select 'Etsy', 'Configurare commissioni'
where not exists (select 1 from piattaforma where nome = 'Etsy');
