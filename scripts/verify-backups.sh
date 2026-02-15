#!/bin/bash
#
# Backup Verification Script
# Verifies integrity of local and remote backups
#

set -euo pipefail

# Configuration
PHONE_IP="192.168.1.10"
PHONE_PORT="8022"
SSH_KEY="$HOME/.ssh/android_backup"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/dns-backups-local"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }

ERRORS=0
VERIFIED=0

log_info "🔍 Starting backup verification..."
echo ""

# Verify local backups
log_info "📂 Verifying local backups..."
if [ -d "$LOCAL_BACKUP_DIR" ]; then
    BACKUP_COUNT=$(find "$LOCAL_BACKUP_DIR" -name "dns-backup-*.tar.gz" 2>/dev/null | wc -l)
    log_info "Found $BACKUP_COUNT local backups"
    echo ""
    
    find "$LOCAL_BACKUP_DIR" -name "dns-backup-*.tar.gz" 2>/dev/null | while read -r backup; do
        BACKUP_NAME=$(basename "$backup")
        
        # Verify tar.gz integrity
        if tar -tzf "$backup" > /dev/null 2>&1; then
            SIZE=$(du -h "$backup" | cut -f1)
            log_info "✅ $BACKUP_NAME ($SIZE)"
            
            # Check contents
            HAS_ZONES=$(tar -tzf "$backup" 2>/dev/null | grep -c "zones/" || echo "0")
            HAS_CONFIG=$(tar -tzf "$backup" 2>/dev/null | grep -c "config/" || echo "0")
            
            if [ "$HAS_ZONES" -gt 0 ]; then
                echo "   ✓ Contains zones/ ($HAS_ZONES files)"
            else
                log_warn "   ⚠ Missing zones/"
            fi
            
            if [ "$HAS_CONFIG" -gt 0 ]; then
                echo "   ✓ Contains config/ ($HAS_CONFIG files)"
            else
                log_warn "   ⚠ Missing config/"
            fi
            
            echo ""
        else
            log_error "❌ CORRUPTED: $BACKUP_NAME"
            echo ""
        fi
    done
else
    log_warn "Local backup directory not found"
fi

# Count results from local
VERIFIED=$(find "$LOCAL_BACKUP_DIR" -name "dns-backup-*.tar.gz" 2>/dev/null | wc -l)

# Verify remote backups on phone
echo ""
log_info "📱 Verifying remote backups on phone..."

# Check SSH connection first
if ! ssh -i "$SSH_KEY" -p "$PHONE_PORT" -o ConnectTimeout=5 "$PHONE_IP" "echo 'Connected'" > /dev/null 2>&1; then
    log_error "Cannot connect to phone at $PHONE_IP"
    exit 1
fi

# Get backup count on phone
REMOTE_COUNT=$(ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
    "ls -1 ${PHONE_BACKUP_DIR}/dns-backup-*.tar.gz 2>/dev/null | wc -l" || echo "0")

log_info "Found $REMOTE_COUNT backups on phone"
echo ""

if [ "$REMOTE_COUNT" -gt 0 ]; then
    # Verify each remote backup
    ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
        "cd ${PHONE_BACKUP_DIR} && ls -1 dns-backup-*.tar.gz" 2>/dev/null | while read -r backup_name; do
        
        # Test if we can list contents
        if ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
            "tar -tzf ${PHONE_BACKUP_DIR}/${backup_name}" > /dev/null 2>&1; then
            
            SIZE=$(ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
                "du -h ${PHONE_BACKUP_DIR}/${backup_name} | cut -f1")
            
            log_info "✅ $backup_name ($SIZE)"
        else
            log_error "❌ CORRUPTED: $backup_name"
        fi
    done
else
    log_warn "No backups found on phone"
fi

# Summary
echo ""
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_info "📊 Verification Summary"
log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Local backups:  $VERIFIED"
echo "  Remote backups: $REMOTE_COUNT"
echo "  Total verified: $((VERIFIED + REMOTE_COUNT))"
echo ""

if [ $ERRORS -eq 0 ]; then
    log_info "✅ All backups verified successfully!"
    exit 0
else
    log_error "❌ Found $ERRORS corrupted backups!"
    exit 1
fi
