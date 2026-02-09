#!/bin/bash
#
# Incremental Backup Script
# Only backs up files that changed since last full backup
#

set -euo pipefail

# Configuration
PHONE_IP="192.168.1.10"
PHONE_PORT="8022"
SSH_KEY="$HOME/.ssh/android_backup"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/dns-backups-local"
PROJECT_DIR="$HOME/projects/dns-automation"
SNAPSHOT_FILE="$LOCAL_BACKUP_DIR/.last-backup-snapshot"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

mkdir -p "$LOCAL_BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DAY_OF_WEEK=1

# Determine if this should be full or incremental
if [ "$DAY_OF_WEEK" = "7" ] || [ ! -f "$SNAPSHOT_FILE" ]; then
    # Sunday or no previous backup = FULL backup
    BACKUP_TYPE="full"
    INCREMENTAL_FLAG=""
    log_info "Creating FULL backup"
else
    # Weekday = INCREMENTAL backup
    BACKUP_TYPE="incremental"
    INCREMENTAL_FLAG="--listed-incremental=$SNAPSHOT_FILE"
    log_info "Creating INCREMENTAL backup (changes since last full)"
fi

BACKUP_NAME="dns-backup-${BACKUP_TYPE}-${TIMESTAMP}.tar.gz"
LOCAL_BACKUP_PATH="${LOCAL_BACKUP_DIR}/${BACKUP_NAME}"

log_info "Backup name: ${BACKUP_NAME}"

# Create backup with incremental support
cd "$PROJECT_DIR"

tar $INCREMENTAL_FLAG -czf "$LOCAL_BACKUP_PATH" \
    zones/ \
    config/ \
    cli/src/ \
    gui/ \
    2>/dev/null || true

BACKUP_SIZE=$(du -h "$LOCAL_BACKUP_PATH" | cut -f1)
log_info "Backup created: ${BACKUP_SIZE}"

# Transfer to phone
log_info "Transferring to phone..."
scp -i "$SSH_KEY" -P "$PHONE_PORT" "$LOCAL_BACKUP_PATH" "${PHONE_IP}:${PHONE_BACKUP_DIR}/" 2>/dev/null

# Retention
if [ "$BACKUP_TYPE" = "full" ]; then
    RETENTION=4  # Keep 4 weekly full backups
else
    RETENTION=7  # Keep 7 daily incremental backups
fi

log_info "Rotating old ${BACKUP_TYPE} backups (keeping last ${RETENTION})..."
ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
    "cd ${PHONE_BACKUP_DIR} && ls -t dns-backup-${BACKUP_TYPE}-*.tar.gz 2>/dev/null | tail -n +$((RETENTION + 1)) | xargs -r rm" 2>/dev/null || true

log_info "✅ ${BACKUP_TYPE} backup complete! (${BACKUP_SIZE})"

# Show comparison
if [ "$BACKUP_TYPE" = "incremental" ]; then
    LAST_FULL=$(ls -t "$LOCAL_BACKUP_DIR"/dns-backup-full-*.tar.gz 2>/dev/null | head -1)
    if [ -n "$LAST_FULL" ]; then
        FULL_SIZE=$(du -h "$LAST_FULL" | cut -f1)
        log_info "💾 Space saved: Full=${FULL_SIZE}, Incremental=${BACKUP_SIZE}"
    fi
fi
