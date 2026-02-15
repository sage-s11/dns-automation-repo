#!/bin/bash
# Safe zone reload script with validation and rollback

set -e

# Configuration
ZONE_FILE="${1}"
ZONE_NAME="${2:-examplenv.demo}"
CONTAINER_NAME="bind9-demo"
BACKUP_DIR="$(dirname "$ZONE_FILE")/backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Validate inputs
if [ -z "$ZONE_FILE" ]; then
    log_error "Usage: $0 <zone-file> [zone-name]"
    echo "Example: $0 zones/db.examplenv.demo examplenv.demo"
    exit 1
fi

if [ ! -f "$ZONE_FILE" ]; then
    log_error "Zone file not found: $ZONE_FILE"
    exit 1
fi

# Check if container is running
if ! podman ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    log_error "Container '${CONTAINER_NAME}' is not running"
    exit 1
fi

log_info "Starting safe reload for zone: $ZONE_NAME"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Backup current zone file
BACKUP_FILE="${BACKUP_DIR}/$(basename "$ZONE_FILE").${TIMESTAMP}"
log_info "Creating backup: $BACKUP_FILE"
cp "$ZONE_FILE" "$BACKUP_FILE"

# Validate zone file syntax
log_info "Validating zone file syntax..."
if ! podman exec "$CONTAINER_NAME" named-checkzone "$ZONE_NAME" "/zones/$(basename "$ZONE_FILE")" > /tmp/checkzone.log 2>&1; then
    log_error "Zone file validation failed!"
    cat /tmp/checkzone.log
    log_info "Backup available at: $BACKUP_FILE"
    exit 1
fi

log_info "Zone file validation passed ✓"

# Reload zone
log_info "Reloading zone..."
if ! podman exec "$CONTAINER_NAME" rndc -p 1953 reload "$ZONE_NAME" > /tmp/reload.log 2>&1; then
    log_error "Zone reload failed!"
    cat /tmp/reload.log
    
    log_warn "Rolling back to previous version..."
    cp "$BACKUP_FILE" "$ZONE_FILE"
    
    if podman exec "$CONTAINER_NAME" rndc -p 1953 reload "$ZONE_NAME" 2>&1; then
        log_info "Rollback successful - old zone restored"
    else
        log_error "Rollback failed - manual intervention required!"
    fi
    exit 1
fi

log_info "Zone reload successful ✓"

# Get serial number
SERIAL=$(podman exec "$CONTAINER_NAME" rndc -p 1953 zonestatus "$ZONE_NAME" 2>/dev/null | grep "serial" | awk '{print $2}' || echo "unknown")
log_info "Zone loaded with serial: $SERIAL"

# Keep only last 10 backups
ls -t "${BACKUP_DIR}/$(basename "$ZONE_FILE")."* 2>/dev/null | tail -n +11 | xargs -r rm

log_info "✓ Reload completed successfully!"
echo ""
echo "Summary:"
echo "  Zone: $ZONE_NAME"
echo "  Serial: $SERIAL"
echo "  Backup: $BACKUP_FILE"
