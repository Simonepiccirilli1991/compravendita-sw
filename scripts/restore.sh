#!/usr/bin/env sh
# Ripristina un backup creato da backup.sh. ATTENZIONE: sostituisce i dati attuali.
# Uso: ./scripts/restore.sh backups/2026-10-13_120000
set -eu

if [ $# -ne 1 ]; then
    echo "Uso: $0 <cartella-backup>" >&2
    exit 1
fi
CARTELLA=$1
RADICE=$(cd "$(dirname "$0")/.." && pwd)
CONTAINER=compravendita-db
DATABASE=${DB_NAME:-compravendita}
UTENTE=${DB_USER:-compravendita}

[ -f "$CARTELLA/database.dump" ] || { echo "File $CARTELLA/database.dump non trovato" >&2; exit 1; }

printf "I dati attuali del database '%s' verranno sostituiti. Scrivi SI per continuare: " "$DATABASE"
read -r CONFERMA
[ "$CONFERMA" = "SI" ] || { echo "Ripristino annullato"; exit 1; }

echo "Ripristino del database..."
docker exec -i "$CONTAINER" pg_restore -U "$UTENTE" -d "$DATABASE" --clean --if-exists --no-owner < "$CARTELLA/database.dump"

if [ -f "$CARTELLA/uploads.tar.gz" ]; then
    echo "Ripristino di foto e allegati..."
    mkdir -p "$RADICE/data/uploads"
    tar -xzf "$CARTELLA/uploads.tar.gz" -C "$RADICE/data/uploads"
fi

echo "Ripristino completato. Puoi riavviare l'applicazione."
