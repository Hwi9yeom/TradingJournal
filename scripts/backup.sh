#!/bin/bash
# PostgreSQL Backup Script for Trading Journal
# Runs daily at 03:00 KST, retains 7 days of backups

set -e

# Configuration
BACKUP_DIR="${BACKUP_DIR:-/backups}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-tradingjournal}"
DB_USER="${DB_USER:-journal}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

# Timestamp format
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/tradingjournal_${TIMESTAMP}.sql.gz"

# Create backup directory if not exists
mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup..."

# Create backup with pg_dump and compress with gzip
PGPASSWORD="${DB_PASSWORD}" pg_dump \
    -h "${DB_HOST}" \
    -p "${DB_PORT}" \
    -U "${DB_USER}" \
    -d "${DB_NAME}" \
    --no-owner \
    --no-privileges \
    | gzip > "${BACKUP_FILE}"

# Verify backup was created and has content
if [ -s "${BACKUP_FILE}" ]; then
    SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup completed: ${BACKUP_FILE} (${SIZE})"
else
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: Backup file is empty or not created!"
    exit 1
fi

# Delete backups older than RETENTION_DAYS
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleaning up backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -name "tradingjournal_*.sql.gz" -type f -mtime +${RETENTION_DAYS} -delete

# List remaining backups
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Current backups:"
ls -lh "${BACKUP_DIR}"/tradingjournal_*.sql.gz 2>/dev/null || echo "  No backups found"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup process finished."
