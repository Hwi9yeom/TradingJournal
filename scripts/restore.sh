#!/bin/bash
# PostgreSQL Restore Script for Trading Journal
# Usage: ./restore.sh [backup_file]

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/backups}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-tradingjournal}"
DB_USER="${DB_USER:-journal}"

# Get backup file from argument or use latest
if [ -n "$1" ]; then
    BACKUP_FILE="$1"
else
    # Find most recent backup
    BACKUP_FILE=$(ls -t "${BACKUP_DIR}"/tradingjournal_*.sql.gz 2>/dev/null | head -1)
fi

if [ -z "${BACKUP_FILE}" ] || [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: No backup file found!"
    echo "Usage: $0 [backup_file]"
    echo "Available backups:"
    ls -lh "${BACKUP_DIR}"/tradingjournal_*.sql.gz 2>/dev/null || echo "  None"
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting restore from: ${BACKUP_FILE}"
echo "WARNING: This will overwrite all data in database '${DB_NAME}'!"
read -p "Are you sure? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

# Terminate existing connections
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Terminating existing connections..."
PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d postgres \
    -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND pid <> pg_backend_pid();" \
    2>/dev/null || true

# Drop and recreate database
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Recreating database..."
PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d postgres \
    -c "DROP DATABASE IF EXISTS ${DB_NAME}; CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

# Restore from backup
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Restoring data..."
gunzip -c "${BACKUP_FILE}" | PGPASSWORD="${DB_PASSWORD}" psql \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Restore completed successfully!"
echo "Note: You may need to run Flyway migration if schema has changed."
