# Multi-Zone Support - Installation Guide

## 📂 Files Created

### New Java Files (Zone Management):

```
cli/src/com/dnsmanager/
├── models/
│   └── Zone.java                    # NEW
├── services/
│   ├── BindConfigService.java       # NEW
│   └── ZoneManagerService.java      # NEW
└── zonecommands/
    ├── ZoneAddCommand.java           # NEW
    ├── ZoneListCommand.java          # NEW
    ├── ZoneDeleteCommand.java        # NEW
    └── ZoneShowCommand.java          # NEW
```

## 🔨 Installation Steps

### Step 1: Create Directory Structure

```bash
cd ~/projects/dns-automation/cli/src/com/dnsmanager

# Create new directories
mkdir -p zonecommands
```

### Step 2: Copy New Files

```bash
# Copy all downloaded files to correct locations:

# Model
cp Zone.java cli/src/com/dnsmanager/models/

# Services
cp BindConfigService.java cli/src/com/dnsmanager/services/
cp ZoneManagerService.java cli/src/com/dnsmanager/services/

# Commands
cp ZoneAddCommand.java cli/src/com/dnsmanager/zonecommands/
cp ZoneListCommand.java cli/src/com/dnsmanager/zonecommands/
cp ZoneDeleteCommand.java cli/src/com/dnsmanager/zonecommands/
cp ZoneShowCommand.java cli/src/com/dnsmanager/zonecommands/
```

### Step 3: Update DnsManager.java

Add to imports:
```java
import com.dnsmanager.zonecommands.*;
```

Add to initializeServices():
```java
// Zone management services
BindConfigService bindConfigService = new BindConfigService("config/named.conf");
ZoneManagerService zoneManager = new ZoneManagerService("zones", bindConfigService);
```

Add to main() command routing:
```java
// Zone management commands
if (command.equals("zone")) {
    if (args.length < 2) {
        System.err.println("Usage: ./dns zone <add|list|delete|show> [options]");
        System.exit(1);
    }
    
    String zoneCommand = args[1];
    String[] zoneArgs = Arrays.copyOfRange(args, 2, args.length);
    
    switch (zoneCommand) {
        case "add":
            new ZoneAddCommand(zoneManager).execute(zoneArgs);
            break;
        case "list":
            new ZoneListCommand(zoneManager).execute(zoneArgs);
            break;
        case "delete":
            new ZoneDeleteCommand(zoneManager).execute(zoneArgs);
            break;
        case "show":
            new ZoneShowCommand(zoneManager).execute(zoneArgs);
            break;
        default:
            System.err.println("Unknown zone command: " + zoneCommand);
            System.exit(1);
    }
    return;
}
```

### Step 4: Compile Everything

```bash
cd ~/projects/dns-automation/cli/src

# Compile models
javac com/dnsmanager/models/*.java

# Compile services
javac com/dnsmanager/services/*.java

# Compile zone commands
javac com/dnsmanager/zonecommands/*.java

# Compile main
javac com/dnsmanager/DnsManager.java
```

### Step 5: Test

```bash
cd ~/projects/dns-automation

# List current zones
./dns zone list

# Add a new zone
./dns zone add example.com 192.168.1.17

# Show zone details
./dns zone show example.com

# List zones again
./dns zone list
```

## 🧪 Complete Test Workflow

```bash
# 1. Create a new zone
./dns zone add testzone.local 192.168.1.17

# 2. Verify it was created
./dns zone list

# 3. Show zone details
./dns zone show testzone.local

# 4. Add a record to the new zone
# (We'll update existing commands in next step)
# For now, manually verify zone file exists:
ls -la zones/db.testzone.local
cat zones/db.testzone.local

# 5. Check BIND config
cat config/named.conf | grep testzone

# 6. Test DNS query
dig @127.0.0.1 -p 1053 ns1.testzone.local +short
# Should return: 192.168.1.17
```

## ⚠️ Troubleshooting

### Compilation Errors

If you get "cannot find symbol" errors:

```bash
# Make sure all files are in correct locations
find cli/src -name "*.java" -type f

# Recompile in order
cd cli/src
javac com/dnsmanager/models/*.java
javac com/dnsmanager/utils/*.java
javac com/dnsmanager/services/*.java
javac com/dnsmanager/commands/*.java
javac com/dnsmanager/zonecommands/*.java
javac com/dnsmanager/DnsManager.java
```

### Zone Creation Fails

```bash
# Check BIND is running
podman ps | grep bind9-demo

# Check permissions
ls -la config/named.conf
ls -la zones/

# Check BIND logs
podman logs bind9-demo | tail -20
```

### rndc reconfig Fails

```bash
# Make sure BIND container is running
systemctl status dns-server

# Or restart it
sudo systemctl restart dns-server
```

## 📝 Next Steps

After this works:

1. **Update existing commands** to accept --zone flag
2. **Add reverse zone** support
3. **Update GUI** to support multiple zones
4. **Fix SOA parsing** bug

## ✅ Verification Checklist

- [ ] All files copied to correct locations
- [ ] DnsManager.java updated with zone commands
- [ ] Code compiles without errors
- [ ] `./dns zone list` works
- [ ] Can create new zone
- [ ] New zone appears in named.conf
- [ ] Zone file created with correct permissions
- [ ] BIND recognizes new zone (dig query works)

---

**Once this works, we'll update the existing add/update/delete commands to accept --zone parameter!**
