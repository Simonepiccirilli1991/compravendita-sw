-- =====================================================================
-- Categorie per gli oggetti fuori dal core di carte, videogiochi e LEGO:
-- tazze e bicchieri, oggettistica e gadget. Gli articoli esistenti non cambiano.
-- =====================================================================

alter table articolo drop constraint ck_articolo_categoria;
alter table articolo add constraint ck_articolo_categoria check (categoria in
    ('CARTA_SINGOLA', 'CARTA_GRADATA', 'SEALED_TCG', 'VIDEOGIOCO', 'LEGO', 'TAZZA', 'OGGETTISTICA', 'ALTRO'));
