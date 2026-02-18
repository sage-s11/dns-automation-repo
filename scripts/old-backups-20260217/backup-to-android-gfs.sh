#!/bin/bash
#
# Enterprise DNS Backup Script with GFS Retention
# Implements Grandfather-Father-Son backup strategy
#

set -euo pipefail

# Configuration
PHONE_IP="192.168.1.38"
PHONE_PORT="8022"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/dns-backups-local"
PROJECT_DIR="$HOME/projects/dns/dns-automation-repo"

# Retention policies (number of backups to keep)
DAILY_RETENTION=7      # Keep 7 daily backups
WEEKLY_RETENTION=4     # Keep 4 weekly backups
MONTHLY_RETENTION=12   # Keep 12 monthly backups
YEARLY_RETENTION=5     # Keep 5 yearly backups

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Logging
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Create local backup directory
mkdir -p "$LOCAL_BACKUP_DIR"

# Determine backup type based on date
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DATE=$(date +%Y%m%d)
DAY_OF_WEEK=$(date +%u)      # 1-7 (Monday-Sunday)
DAY_OF_MONTH=$(date +%d)     # 01-31
MONTH=$(date +%m)            # 01-12

# Determine backup type and retention
if [ "$DAY_OF_MONTH" = "01" ] && [ "$MONTH" = "01" ]; then
    BACKUP_TYPE="yearly"
    RETENTION=$YEARLY_RETENTION
    log_info "Creating YEARLY backup (New Year's Day)"
elif [ "$DAY_OF_MONTH" = "01" ]; then
    BACKUP_TYPE="monthly"
    RETENTION=$MONTHLY_RETENTION
    log_info "Creating MONTHLY backup (First of month)"
elif [ "$DAY_OF_WEEK" = "7" ]; then
    BACKUP_TYPE="weekly"
    RETENTION=$WEEKLY_RETENTION
    log_info "Creating WEEKLY backup (Sunday)"
else
    BACKUP_TYPE="daily"
    RETENTION=$DAILY_RETENTION
    log_info "Creating DAILY backup"
fi

BACKUP_NAME="dns-backup-${BACKUP_TYPE}-${TIMESTAMP}.tar.gz"
LOCAL_BACKUP_PATH="${LOCAL_BACKUP_DIR}/${BACKUP_NAME}"

log_info "Backup name: ${BACKUP_NAME}"

# Create temporary directory for backup
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

log_info "Gathering files for backup..."

# Copy files to backup
cd "$PROJECT_DIR"
cp -r zones "$TEMP_DIR/" 2>/dev/null || log_warn "No zones directory"
cp -r config "$TEMP_DIR/" 2>/dev/null || log_warn "No config directory"
cp -r cli/src "$TEMP_DIR/cli-src/" 2>/dev/null || log_warn "No CLI source"
cp -r gui "$TEMP_DIR/" 2>/dev/null || log_warn "No GUI directory"
cp scripts/backup-to-android-gfs.sh "$TEMP_DIR/" 2>/dev/null || true

# Create metadata file
cat > "$TEMP_DIR/BACKUP_INFO.txt" << EOF
Backup Information
==================
Type:       $BACKUP_TYPE
Date:       $(date)
Timestamp:  $TIMESTAMP
Hostname:   $(hostname)
Project:    dns-automation

Contents:
- zones/     : DNS zone files
- config/    : BIND configuration
- cli-src/   : CLI source code
- gui/       : Web GUI files

Retention Policy:
- Daily:   $DAILY_RETENTION backups
- Weekly:  $WEEKLY_RETENTION backups
- Monthly: $MONTHLY_RETENTION backups
- Yearly:  $YEARLY_RETENTION backups
EOF

# Create tarball
log_info "Creating compressed archive..."
cd "$TEMP_DIR"
tar -czf "$LOCAL_BACKUP_PATH" . 2>/dev/null

# Get backup size
BACKUP_SIZE=$(du -h "$LOCAL_BACKUP_PATH" | cut -f1)
log_info "Backup created: ${BACKUP_SIZE}"

# Transfer to phone
log_info "Transferring to Android phone (${PHONE_IP})..."

if scp -i "$#SSH_KEY" -P "$PHONE_PORT" "$LOCAL_BACKUP_PATH" "u0_a254@${PHONE_IP}:${PHONE_BACKUP_DIR}/" 2>/dev/null; then
    log_info "✅ Backup transferred successfully!"
else
    log_error "Failed to transfer backup to phone"
    exit 1
fi

# Rotate old backups on phone
log_info "Rotating old ${BACKUP_TYPE} backups on phone (keeping last ${RETENTION})..."
ssh -i "$#SSH_KEY" -p "$PHONE_PORT" "u0_a254@$PHONE_IP" \
    "cd ${PHONE_BACKUP_DIR} && ls -t dns-backup-${BACKUP_TYPE}-*.tar.gz 2>/dev/null | tail -n +$((RETENTION + 1)) | xargs -r rm" \
    2>/dev/null || log_warn "No old backups to rotate"

# Rotate old backups locally
log_info "Rotating old ${BACKUP_TYPE} backups locally (keeping last ${RETENTION})..."
cd "$LOCAL_BACKUP_DIR"
ls -t dns-backup-${BACKUP_TYPE}-*.tar.gz 2>/dev/null | tail -n +$((RETENTION + 1)) | xargs -r rm \
    || log_warn "No old local backups to rotate"

# Show backup summary
log_info "📊 Backup Summary:"
echo "  Type:     ${BACKUP_TYPE}"
echo "  Size:     ${BACKUP_SIZE}"
echo "  Local:    ${LOCAL_BACKUP_PATH}"
echo "  Remote:   ${PHONE_IP}:${PHONE_BACKUP_DIR}/${BACKUP_NAME}"

# Show remaining backups on phone
log_info "📱 Backups on phone:"
ssh -i "$#SSH_KEY" -p "$PHONE_PORT" "u0_a254@$PHONE_IP" \
    "ls -lht ${PHONE_BACKUP_DIR}/ 2>/dev/null | head -10" || log_warn "Could not list phone backups"

# Show local backups
log_info "💻 Local backups:"
ls -lht "$LOCAL_BACKUP_DIR" | head -10

log_info "✅ Backup complete!"
