# Quick Start Guide

## 1. First Import

Create a test CSV:
```bash
cat > test-import.csv << 'CSV'
hostname,ip,type,zone,ttl,priority
web-01,10.10.10.1,A,test.local,3600,
www,web-01.test.local,CNAME,test.local,3600,
mail,mail.test.local,MX,test.local,3600,10
CSV
```

Import it:
```bash
java -cp "lib/*:src" com.dnsmanager.commands.ImportCommand test-import.csv
```

## 2. Export with Analysis

```bash
java -cp "lib/*:src" com.dnsmanager.commands.ExportCommand --zone test.local
```

Check the output:
```bash
cat test-local-export-*.csv
```

## 3. Safe Deletion

Preview deletion:
```bash
java -cp "lib/*:src" com.dnsmanager.commands.DeleteCommand --pattern "test-*" --dry-run
```

Execute deletion:
```bash
java -cp "lib/*:src" com.dnsmanager.commands.DeleteCommand --pattern "test-*"
```

## 4. Network Validation

Import with active IP detection:
```bash
java -cp "lib/*:src" com.dnsmanager.commands.ImportCommand records.csv --validate-network
```

Active IPs will be flagged and skipped for safety!

## Common Commands

**Import:**
- `ImportCommand file.csv` - Basic import
- `ImportCommand file.csv --validate-network` - Check if IPs are active
- `ImportCommand file.csv --dry-run` - Preview only

**Export:**
- `ExportCommand --zone example.com` - Export zone
- `ExportCommand --pattern "web-*"` - Export by pattern
- `ExportCommand --all` - Export everything
- `ExportCommand --zone example.com --split` - Categorized files

**Delete:**
- `DeleteCommand --hostname web-01` - Delete specific record
- `DeleteCommand --pattern "test-*"` - Delete by pattern
- `DeleteCommand --zone test.local` - Delete entire zone
- `DeleteCommand --cleanup-tests` - Smart test data cleanup
- Add `--dry-run` to preview, `--force` to skip validation
