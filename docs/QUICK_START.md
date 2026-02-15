# Quick Start Guide

## Start Container (if stopped):
```bash
cd ~/projects/dns-automation
./bind/run.sh
```

## Daily Commands:
```bash
./dns list                    # See all records
./dns add <host> <ip>         # Add record
./dns status                  # Health check
```

## Emergency Recovery:
```bash
# If something breaks:
ls zones/backups/             # Find backup
cp zones/backups/db.* zones/db.examplenv.demo
./dns reload
```

## Test DNS:
```bash
dig @127.0.0.1 -p 1053 <hostname>.examplenv.demo
```
