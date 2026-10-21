#!/usr/bin/env sh
# Backup del gestionale: database (pg_dump, formato custom) e cartella data/uploads.
# Uso: ./scripts/backup.sh [cartella-destinazione]
set -eu

RADICE=$(cd "$(dirname "$0")/.." && pwd)
DESTINAZIONE=${1:-"$RADICE/backups"}
CONTAINER=compravendita-sw-tazze-db
DATABASE=${DB_NAME:-compravendita}
UTENTE=${DB_USER:-compravendita}

CARTELLA="$DESTINAZIONE/$(date +%Y-%m-%d_%H%M%S)"
mkdir -p "$CARTELLA"

echo "Dump del database '$DATABASE'..."
docker exec "$CONTAINER" pg_dump -U "$UTENTE" -d "$DATABASE" -Fc > "$CARTELLA/database.dump"

if [ -d "$RADICE/data/uploads" ]; then
    echo "Archivio di foto e allegati..."
    tar -czf "$CARTELLA/uploads.tar.gz" -C "$RADICE/data/uploads" .
fi

echo "Backup completato in $CARTELLA"
