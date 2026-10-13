-- =====================================================================
-- Fase 4: vendite, annullamenti e resi
-- =====================================================================

alter table vendita
    -- commissioni calcolate dalla configurazione della piattaforma (false = inserite a mano)
    add column commissioni_automatiche boolean        not null default true,
    -- data e motivo di annullamento o reso
    add column data_storno             date,
    add column motivo_storno           text,
    -- costi rimasti a carico in caso di reso (spedizioni, commissioni non rimborsate)
    add column costi_reso              numeric(12, 2) not null default 0;

alter table vendita add constraint ck_vendita_costi_reso check (costi_reso >= 0);
alter table vendita add constraint ck_vendita_storno check (stato = 'COMPLETATA' or data_storno is not null);
