-- Dati iniziali. Le commissioni sono a 0: vanno configurate dalla sezione Piattaforme
-- con le tariffe reali del proprio account (cambiano nel tempo e per tipo di venditore).

insert into brand (nome) values
    ('Pokémon'),
    ('One Piece'),
    ('Nintendo'),
    ('Sony PlayStation'),
    ('Microsoft Xbox'),
    ('Sega'),
    ('LEGO'),
    ('Altro');

insert into piattaforma (nome, note) values
    ('eBay', 'Configurare commissioni'),
    ('Vinted', 'Configurare commissioni'),
    ('Cardmarket', 'Configurare commissioni'),
    ('Subito', 'Configurare commissioni'),
    ('Wallapop', 'Configurare commissioni'),
    ('Fiera', 'Vendita diretta in fiera/evento'),
    ('Privato', 'Vendita diretta a privato');
