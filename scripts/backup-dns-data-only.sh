#!/bin/bash
#
# DNS Data-Only Backup Script
# Backs up: Database + Zone files + BIND config (NO CODE)
#

set -euo pipefail

# Configuration
PHONE_USER="u0_a254"
PHONE_IP="192.168.1.38"
PHONE_PORT="8022"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/dns-data-backups"

# FIXED PATHS
DNS_ZONES_DIR="$HOME/projects/dns/dns-automation-repo/dns-automation-repo/dns-automation/zones"
DNS_CONFIG_DIR="$HOME/projects/dns/dns-automation-repo/dns-automation-repo/dns-automation/config"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Create local backup directory
mkdir -p "$LOCAL_BACKUP_DIR"

# Timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DATE=$(date +%Y-%m-%d)
BACKUP_NAME="dns-data-${TIMESTAMP}.tar.gz"
LOCAL_BACKUP_PATH="${LOCAL_BACKUP_DIR}/${BACKUP_NAME}"

log_info "📦 Creating DNS data backup..."
log_info "Timestamp: ${TIMESTAMP}"

# Create temporary directory
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

# 1. Export PostgreSQL database
log_info "💾 Exporting PostgreSQL database..."
export PGPASSWORD="root"
if pg_dump -U dnsadmin -h localhost dnsmanager > "$TEMP_DIR/database-dump.sql" 2>/dev/null; then
    DB_SIZE=$(du -h "$TEMP_DIR/database-dump.sql" | cut -f1)
    log_info "   Database exported: ${DB_SIZE}"
else
    log_error "Database export failed!"
    exit 1
fi

# 2. Copy zone files
ZONE_COUNT=0
log_info "📁 Copying DNS zone files..."
if [ -d "$DNS_ZONES_DIR" ]; then
    mkdir -p "$TEMP_DIR/zones"
    cp -r "$DNS_ZONES_DIR"/* "$TEMP_DIR/zones/" 2>/dev/null || true
    ZONE_COUNT=$(find "$TEMP_DIR/zones" -name "db.*" -type f 2>/dev/null | wc -l)
    log_info "   Copied ${ZONE_COUNT} zone files"
else
    log_warn "No zones directory at: $DNS_ZONES_DIR"
fi

# 3. Copy BIND configuration
log_info "⚙️  Copying BIND configuration..."
if [ -d "$DNS_CONFIG_DIR" ]; then
    mkdir -p "$TEMP_DIR/config"
    cp -r "$DNS_CONFIG_DIR"/* "$TEMP_DIR/config/" 2>/dev/null || true
    log_info "   BIND config copied"
else
    log_warn "No config directory at: $DNS_CONFIG_DIR"
fi

# 4. Get database statistics
log_info "📊 Gathering database statistics..."
DB_RECORDS=$(psql -U dnsadmin -h localhost -d dnsmanager -t -c "SELECT COUNT(*) FROM dns_records;" 2>/dev/null | tr -d ' ') || DB_RECORDS="N/A"
DB_ZONES=$(psql -U dnsadmin -h localhost -d dnsmanager -t -c "SELECT COUNT(*) FROM zones;" 2>/dev/null | tr -d ' ') || DB_ZONES="N/A"
DB_TYPES=$(psql -U dnsadmin -h localhost -d dnsmanager -t -c "SELECT type, COUNT(*) FROM dns_records GROUP BY type ORDER BY type;" 2>/dev/null) || DB_TYPES="N/A"

# 5. Create backup metadata
cat > "$TEMP_DIR/BACKUP_INFO.txt" << INNER_EOF
DNS Data Backup
===============
Date:       ${DATE}
Time:       $(date +%H:%M:%S)
Timestamp:  ${TIMESTAMP}
Hostname:   $(hostname)

Database Statistics:
-------------------
Total Records: ${DB_RECORDS}
Total Zones:   ${DB_ZONES}

Record Types:
${DB_TYPES}

Backup Contents:
----------------
1. database-dump.sql : PostgreSQL database (${DB_RECORDS} records)
2. zones/            : BIND zone files (${ZONE_COUNT} files)
3. config/           : BIND9 configuration

Restoration:
-----------
psql -U dnsadmin -d dnsmanager < database-dump.sql

Generated: $(date)
INNER_EOF

# 6. Create compressed backup
log_info "🗜️  Creating compressed archive..."
cd "$TEMP_DIR"
tar -czf "$LOCAL_BACKUP_PATH" . 2>/dev/null

BACKUP_SIZE=$(du -h "$LOCAL_BACKUP_PATH" | cut -f1)
log_info "✅ Backup created: ${BACKUP_SIZE}"

# 7. Transfer to phone
log_info "📱 Transferring to Android phone..."
if scp -P "$PHONE_PORT" "$LOCAL_BACKUP_PATH" "${PHONE_USER}@${PHONE_IP}:${PHONE_BACKUP_DIR}/"; then
    log_info "✅ Transfer successful!"
else
    log_error "Transfer failed"
    exit 1
fi

# 8. Rotate backups (keep last 30)
log_info "🗑️  Rotating old backups..."
ssh -p "$PHONE_PORT" "${PHONE_USER}@${PHONE_IP}" \
    "cd ${PHONE_BACKUP_DIR} && ls -t dns-data-*.tar.gz 2>/dev/null | tail -n +31 | xargs -r rm" || true

cd "$LOCAL_BACKUP_DIR"
ls -t dns-data-*.tar.gz 2>/dev/null | tail -n +31 | xargs -r rm || true

# 9. Summary
echo ""
log_info "📊 Backup Summary:"
echo "  Database:  ${DB_RECORDS} records, ${DB_ZONES} zones"
echo "  Zones:     ${ZONE_COUNT} files"
echo "  Size:      ${BACKUP_SIZE}"
echo "  Local:     ${LOCAL_BACKUP_PATH}"
echo "  Phone:     ${PHONE_USER}@${PHONE_IP}:${PHONE_BACKUP_DIR}/${BACKUP_NAME}"

log_info "✅ Backup complete!"
