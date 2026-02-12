# DNS/DHCP/IPAM Management Platform

Enterprise-grade DNS management system built with Java and PostgreSQL.

## Features

✅ **Import** - Bulk CSV import with validation  
✅ **Export** - Smart export with network analysis  
✅ **Delete** - Safe deletion with active IP detection  
✅ **Record Types** - A, CNAME, MX, TXT support  
✅ **Network Validation** - Parallel TCP/ICMP checking (40 threads)  
✅ **Performance** - 40% optimized, 281x faster than manual  

## Quick Start

### Prerequisites
- Java 17+
- PostgreSQL 16+
- BIND9 (optional)

### Setup
```bash
# 1. Initialize database
psql -U postgres -f ../database/schema.sql

# 2. Configure connection
# Edit src/com/dnsmanager/config/DatabaseConfig.java

# 3. Compile
cd src
javac -cp "../lib/*:." com/dnsmanager/commands/*.java
```

### Usage

**Import DNS records:**
```bash
cd ~/projects/dns-automation/cli
java -cp "lib/*:src" com.dnsmanager.commands.ImportCommand records.csv --validate-network
```

**Export with analysis:**
```bash
java -cp "lib/*:src" com.dnsmanager.commands.ExportCommand --zone example.com
```

**Delete records:**
```bash
java -cp "lib/*:src" com.dnsmanager.commands.DeleteCommand --pattern "test-*" --dry-run
```

## Supported Record Types

| Type | Description | Example |
|------|-------------|---------|
| A | IPv4 address | `web-01,10.10.10.1,A,example.com,3600,` |
| CNAME | Canonical name | `www,web-01.example.com,CNAME,example.com,3600,` |
| MX | Mail exchange | `mail,mail.example.com,MX,example.com,3600,10` |
| TXT | Text record | `spf,v=spf1 mx ~all,TXT,example.com,3600,` |

## CSV Format

```csv
hostname,ip,type,zone,ttl,priority
web-01,10.10.10.1,A,example.com,3600,
www,web-01.example.com,CNAME,example.com,3600,
mail,mail.example.com,MX,example.com,3600,10
spf-record,v=spf1 mx ~all,TXT,example.com,3600,
```

## Architecture

```
cli/
├── src/com/dnsmanager/
│   ├── commands/         # CLI commands
│   │   ├── ImportCommand.java
│   │   ├── ExportCommand.java
│   │   └── DeleteCommand.java
│   ├── services/         # Business logic
│   │   ├── EnterpriseNetworkValidator.java
│   │   ├── RuleBasedValidator.java
│   │   ├── ExportService.java
│   │   └── CleanupService.java
│   ├── models/          # Data models
│   │   ├── DnsRecord.java
│   │   └── Zone.java
│   └── config/          # Configuration
│       └── DatabaseConfig.java
├── lib/                 # Dependencies
├── outputs/             # Export files
└── test-data/          # Test CSV files
```

## Performance

- **Import:** 7.9s for 30 records (with network validation)
- **Export:** 4.8s for 10 records (with analysis)
- **Delete:** 4.8s validation + instant deletion
- **Network Validation:** 40 parallel threads, 150ms TCP timeout

## Technologies

- Java 17
- PostgreSQL 16
- HikariCP (connection pooling)
- BIND9 (DNS server)
- Concurrent programming (ExecutorService)

## License

MIT License

## Author

Built as part of enterprise DNS automation project.
