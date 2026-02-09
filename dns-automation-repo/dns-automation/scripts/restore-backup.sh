#!/bin/bash
#
# DNS Backup Restore Script
# Restores DNS system from backup
#

set -euo pipefail

# Configuration
PHONE_IP="192.168.1.10"
PHONE_PORT="8022"
SSH_KEY="$HOME/.ssh/android_backup"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/dns-backups-local"
PROJECT_DIR="$HOME/projects/dns-automation"
RESTORE_DIR="/tmp/dns-restore-$$"

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_prompt() { echo -e "${CYAN}[?]${NC} $1"; }

# Cleanup on exit
trap "rm -rf $RESTORE_DIR" EXIT

show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -l, --local          Restore from local backup"
    echo "  -r, --remote         Restore from phone backup"
    echo "  -f, --file <file>    Specify backup file to restore"
    echo "  -h, --help           Show this help"
    echo ""
    echo "Examples:"
    echo "  $0 --local                    # Interactive local restore"
    echo "  $0 --remote                   # Interactive remote restore"
    echo "  $0 --local --file backup.tar.gz"
    exit 0
}

list_local_backups() {
    log_info "Available local backups:"
    echo ""
    ls -lht "$LOCAL_BACKUP_DIR"/dns-backup-*.tar.gz 2>/dev/null | \
        awk '{print NR". "$9" ("$5")"}' | \
        while read line; do
            echo "  $line"
        done
    echo ""
}

list_remote_backups() {
    log_info "Available backups on phone:"
    echo ""
    ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
        "ls -lht ${PHONE_BACKUP_DIR}/dns-backup-*.tar.gz 2>/dev/null" | \
        awk '{print NR". "$9" ("$5")"}' | \
        while read line; do
            echo "  $line"
        done
    echo ""
}

restore_from_backup() {
    local backup_file="$1"
    local source="$2"
    
    log_info "🔄 Starting restore process..."
    echo ""
    
    # Create restore directory
    mkdir -p "$RESTORE_DIR"
    
    # Extract backup
    log_info "Extracting backup..."
    if [ "$source" = "remote" ]; then
        # Download from phone first
        scp -i "$SSH_KEY" -P "$PHONE_PORT" \
            "${PHONE_IP}:${PHONE_BACKUP_DIR}/$(basename $backup_file)" \
            "$RESTORE_DIR/backup.tar.gz" 2>/dev/null
        backup_file="$RESTORE_DIR/backup.tar.gz"
    fi
    
    tar -xzf "$backup_file" -C "$RESTORE_DIR"
    log_info "✅ Backup extracted"
    echo ""
    
    # Show what will be restored
    log_info "📦 Backup contains:"
    if [ -f "$RESTORE_DIR/BACKUP_INFO.txt" ]; then
        cat "$RESTORE_DIR/BACKUP_INFO.txt"
        echo ""
    fi
    
    # Confirm restore
    log_warn "⚠️  This will OVERWRITE current DNS configuration!"
    log_prompt "Continue with restore? (yes/no): "
    read -r response
    
    if [ "$response" != "yes" ]; then
        log_info "Restore cancelled"
        exit 0
    fi
    
    # Create current backup before restore
    log_info "Creating safety backup of current state..."
    SAFETY_BACKUP="$LOCAL_BACKUP_DIR/dns-backup-pre-restore-$(date +%Y%m%d_%H%M%S).tar.gz"
    cd "$PROJECT_DIR"
    tar -czf "$SAFETY_BACKUP" zones/ config/ cli/src/ gui/ 2>/dev/null || true
    log_info "✅ Safety backup: $SAFETY_BACKUP"
    echo ""
    
    # Restore files
    log_info "Restoring files..."
    
    if [ -d "$RESTORE_DIR/zones" ]; then
        log_info "  Restoring zones/"
        rm -rf "$PROJECT_DIR/zones"
        cp -r "$RESTORE_DIR/zones" "$PROJECT_DIR/"
    fi
    
    if [ -d "$RESTORE_DIR/config" ]; then
        log_info "  Restoring config/"
        rm -rf "$PROJECT_DIR/config"
        cp -r "$RESTORE_DIR/config" "$PROJECT_DIR/"
    fi
    
    if [ -d "$RESTORE_DIR/cli-src" ]; then
        log_info "  Restoring cli/src/"
        rm -rf "$PROJECT_DIR/cli/src"
        mkdir -p "$PROJECT_DIR/cli"
        cp -r "$RESTORE_DIR/cli-src" "$PROJECT_DIR/cli/src"
    fi
    
    if [ -d "$RESTORE_DIR/gui" ]; then
        log_info "  Restoring gui/"
        rm -rf "$PROJECT_DIR/gui"
        cp -r "$RESTORE_DIR/gui" "$PROJECT_DIR/"
    fi
    
    log_info "✅ Files restored successfully!"
    echo ""
    
    # Reload DNS
    log_info "Reloading DNS server..."
    if sudo systemctl is-active dns-server > /dev/null 2>&1; then
        sudo systemctl restart dns-server
        log_info "✅ DNS server restarted"
    else
        log_warn "DNS server not running, skipping reload"
    fi
    
    echo ""
    log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_info "✅ Restore Complete!"
    log_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    log_info "Safety backup saved at:"
    echo "  $SAFETY_BACKUP"
    echo ""
    log_info "Verify DNS is working:"
    echo "  ./dns list"
    echo "  dig @127.0.0.1 -p 1053 ns1.examplenv.demo +short"
}

# Parse arguments
SOURCE=""
BACKUP_FILE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        -l|--local)
            SOURCE="local"
            shift
            ;;
        -r|--remote)
            SOURCE="remote"
            shift
            ;;
        -f|--file)
            BACKUP_FILE="$2"
            shift 2
            ;;
        -h|--help)
            show_usage
            ;;
        *)
            log_error "Unknown option: $1"
            show_usage
            ;;
    esac
done

# Interactive mode if no file specified
if [ -z "$BACKUP_FILE" ]; then
    if [ -z "$SOURCE" ]; then
        log_prompt "Restore from (1) Local or (2) Remote? [1/2]: "
        read -r choice
        if [ "$choice" = "1" ]; then
            SOURCE="local"
        elif [ "$choice" = "2" ]; then
            SOURCE="remote"
        else
            log_error "Invalid choice"
            exit 1
        fi
    fi
    
    if [ "$SOURCE" = "local" ]; then
        list_local_backups
        log_prompt "Enter backup number to restore: "
        read -r backup_num
        BACKUP_FILE=$(ls -t "$LOCAL_BACKUP_DIR"/dns-backup-*.tar.gz 2>/dev/null | sed -n "${backup_num}p")
    else
        list_remote_backups
        log_prompt "Enter backup number to restore: "
        read -r backup_num
        BACKUP_FILE=$(ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" \
            "ls -t ${PHONE_BACKUP_DIR}/dns-backup-*.tar.gz 2>/dev/null" | sed -n "${backup_num}p")
    fi
fi

if [ -z "$BACKUP_FILE" ]; then
    log_error "No backup file selected"
    exit 1
fi

log_info "Selected: $(basename $BACKUP_FILE)"
echo ""

restore_from_backup "$BACKUP_FILE" "$SOURCE"
