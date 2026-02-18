#!/bin/bash

# DNS Backup to Android Phone Script
# Backs up zones, configs, and database to Android via SSH

set -e

# Configuration
PHONE_IP="192.168.1.38"
PHONE_PORT="8022"
SSH_KEY="$HOME/.ssh/android_backup"
PHONE_BACKUP_DIR="dns-backups"
LOCAL_BACKUP_DIR="$HOME/projects/dns-automation"

# Timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_NAME="dns-backup-${TIMESTAMP}.tar.gz"

echo "🔄 Creating DNS backup..."

# Create temporary backup directory
TEMP_DIR=$(mktemp -d)

# Copy files to backup
cp -r zones "$TEMP_DIR/"
cp -r config "$TEMP_DIR/"
cp -r cli "$TEMP_DIR/"
cp -r gui "$TEMP_DIR/" 2>/dev/null || true

# Create tarball
cd "$TEMP_DIR"
tar -czf "/tmp/${BACKUP_NAME}" .
cd -

echo "✅ Backup created: ${BACKUP_NAME}"
echo "📱 Transferring to Android phone..."

# Transfer to phone
scp -i "$SSH_KEY" -P "$PHONE_PORT" "/tmp/${BACKUP_NAME}" "${PHONE_IP}:${PHONE_BACKUP_DIR}/"

if [ $? -eq 0 ]; then
    echo "✅ Backup transferred successfully!"
    echo "📦 Location: ${PHONE_IP}:~/${PHONE_BACKUP_DIR}/${BACKUP_NAME}"
    
    # Cleanup
    rm -rf "$TEMP_DIR"
    rm "/tmp/${BACKUP_NAME}"
    
    # Show backup size
    ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" "ls -lh ${PHONE_BACKUP_DIR}/${BACKUP_NAME}"
else
    echo "❌ Backup transfer failed!"
    exit 1
# Rotate old backups (keep last 30)
    echo "🗑️  Cleaning old backups (keeping last 30)..."
    ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" "cd ${PHONE_BACKUP_DIR} && ls -t dns-backup-*.tar.gz | tail -n +31 | xargs -r rm"
    
    # Show remaining backups
    echo "📋 Backups on phone:"
    ssh -i "$SSH_KEY" -p "$PHONE_PORT" "$PHONE_IP" "ls -lht ${PHONE_BACKUP_DIR}/ | head -10"
fi
