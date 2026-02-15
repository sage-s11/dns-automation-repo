# Zone Flag Support Installation Guide

## 📦 What's New

This update adds `--zone` flag support to all record commands, allowing you to manage records across multiple zones!

---

## 🎯 New Functionality

### Before (Single Zone Only):
```bash
./dns add www 10.10.10.10  # Always adds to examplenv.demo
```

### After (Multi-Zone):
```bash
# Add to default zone (examplenv.demo)
./dns add www 10.10.10.10

# Add to specific zone
./dns add www 10.10.10.10 --zone testzone5.local
./dns add api 10.10.10.20 --zone example.com

# List records from specific zone
./dns list --zone testzone5.local

# Update/delete from specific zone
./dns update www 10.10.10.50 --zone testzone5.local
./dns delete www --zone testzone5.local --force
```

---

## 📂 Files Created

**New Service Files:**
1. `ZoneFileServiceDynamic.java` - Works with any zone file
2. `DnsServerServiceDynamic.java` - Reloads any zone

**Updated Command Files:**
3. `AddCommandWithZone.java` - Add with --zone flag
4. `UpdateCommandWithZone.java` - Update with --zone flag
5. `SetCommandWithZone.java` - Set with --zone flag
6. `DeleteCommandWithZone.java` - Delete with --zone flag
7. `ListCommandWithZone.java` - List with --zone flag

---

## 🔨 Installation Steps

### Step 1: Copy New Files

```bash
cd ~/projects/dns-automation

# Copy service files
cp ZoneFileServiceDynamic.java cli/src/com/dnsmanager/services/
cp DnsServerServiceDynamic.java cli/src/com/dnsmanager/services/

# Rename old command files (backup)
cd cli/src/com/dnsmanager/commands
mv AddCommand.java AddCommand.java.old
mv UpdateCommand.java UpdateCommand.java.old
mv SetCommand.java SetCommand.java.old
mv DeleteCommand.java DeleteCommand.java.old
mv ListCommand.java ListCommand.java.old

# Copy new command files
cd ~/projects/dns-automation
cp AddCommandWithZone.java cli/src/com/dnsmanager/commands/AddCommand.java
cp UpdateCommandWithZone.java cli/src/com/dnsmanager/commands/UpdateCommand.java
cp SetCommandWithZone.java cli/src/com/dnsmanager/commands/SetCommand.java
cp DeleteCommandWithZone.java cli/src/com/dnsmanager/commands/DeleteCommand.java
cp ListCommandWithZone.java cli/src/com/dnsmanager/commands/ListCommand.java
```

---

### Step 2: Update DnsManager.java

```bash
nano cli/src/com/dnsmanager/DnsManager.java
```

**Find the initializeServices() method and UPDATE it:**

**OLD:**
```java
private static void initializeServices() throws IOException {
    zoneService = new ZoneFileService(ZONE_NAME);
    serverService = new DnsServerService(CONTAINER_NAME, ZONE_NAME, RNDC_PORT);
    backupService = new BackupService(backupDir, MAX_BACKUPS);
    bindConfigService = new BindConfigService("config/named.conf");
    zoneManager = new ZoneManagerService("zones", bindConfigService);
}
```

**NEW:**
```java
private static ZoneFileServiceDynamic zoneServiceDynamic;
private static DnsServerServiceDynamic serverServiceDynamic;

private static void initializeServices() throws IOException {
    // OLD services (keep for backward compatibility temporarily)
    zoneService = new ZoneFileService(ZONE_NAME);
    serverService = new DnsServerService(CONTAINER_NAME, ZONE_NAME, RNDC_PORT);
    backupService = new BackupService(backupDir, MAX_BACKUPS);
    
    // NEW dynamic services
    zoneServiceDynamic = new ZoneFileServiceDynamic("zones");
    serverServiceDynamic = new DnsServerServiceDynamic(CONTAINER_NAME, RNDC_PORT);
    
    // Zone management
    bindConfigService = new BindConfigService("config/named.conf");
    zoneManager = new ZoneManagerService("zones", bindConfigService);
}
```

**Find the initializeCommands() method and UPDATE it:**

**OLD:**
```java
private static void initializeCommands() {
    commands = new HashMap<>();
    commands.put("list", new ListCommand(zoneService));
    commands.put("add", new AddCommand(zoneService, serverService, backupService));
    commands.put("update", new UpdateCommand(zoneService, serverService, backupService));
    commands.put("set", new SetCommand(zoneService, serverService, backupService));
    commands.put("delete", new DeleteCommand(zoneService, serverService, backupService));
    // ... other commands
}
```

**NEW:**
```java
private static void initializeCommands() {
    commands = new HashMap<>();
    commands.put("list", new ListCommand(zoneServiceDynamic, bindConfigService, ZONE_NAME));
    commands.put("add", new AddCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
    commands.put("update", new UpdateCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
    commands.put("set", new SetCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
    commands.put("delete", new DeleteCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
    // ... other commands (keep as-is)
}
```

---

### Step 3: Compile Everything

```bash
cd ~/projects/dns-automation/cli/src

# Compile new services
javac com/dnsmanager/services/ZoneFileServiceDynamic.java
javac com/dnsmanager/services/DnsServerServiceDynamic.java

# Compile updated commands
javac com/dnsmanager/commands/*.java

# Compile main
javac com/dnsmanager/DnsManager.java

cd ../..
```

---

### Step 4: Test!

```bash
# Create test zones
./dns zone add testzone.local 192.168.1.17
./dns zone add example.com 192.168.1.17

# Add records to different zones
./dns add www 10.10.10.10 --zone testzone.local
./dns add api 10.10.10.20 --zone example.com
./dns add mail 10.10.10.30  # Default zone (examplenv.demo)

# List records from each zone
./dns list --zone testzone.local
./dns list --zone example.com
./dns list  # Default zone

# Test DNS queries
dig @127.0.0.1 -p 1053 www.testzone.local +short
dig @127.0.0.1 -p 1053 api.example.com +short
dig @127.0.0.1 -p 1053 mail.examplenv.demo +short
```

---

## 🎯 Complete Test Workflow

```bash
# 1. Create zones
./dns zone add mysite.org 192.168.1.17
./dns zone list

# 2. Add records
./dns add www 10.10.10.10 --zone mysite.org
./dns add blog 10.10.10.20 --zone mysite.org
./dns add api 10.10.10.30 --zone mysite.org

# 3. List records
./dns list --zone mysite.org

# 4. Test DNS
dig @127.0.0.1 -p 1053 www.mysite.org +short  # → 10.10.10.10
dig @127.0.0.1 -p 1053 blog.mysite.org +short # → 10.10.10.20

# 5. Update record
./dns update www 10.10.10.50 --zone mysite.org --force

# 6. Delete record
./dns delete blog --zone mysite.org --force

# 7. Verify changes
./dns list --zone mysite.org
```

---

## ✅ Success Criteria

- [ ] Can add records to specific zones with --zone flag
- [ ] Can list records from specific zones
- [ ] Can update records in specific zones
- [ ] Can delete records from specific zones
- [ ] Default zone (examplenv.demo) still works without --zone flag
- [ ] DNS queries work for all zones
- [ ] No compilation errors

---

## ⚠️ Troubleshooting

### Compilation Errors

```bash
# If you get "cannot find symbol" errors:
cd cli/src

# Recompile in order
javac com/dnsmanager/models/*.java
javac com/dnsmanager/utils/*.java
javac com/dnsmanager/services/*.java
javac com/dnsmanager/commands/*.java
javac com/dnsmanager/zonecommands/*.java
javac com/dnsmanager/DnsManager.java
```

### Command Not Working

```bash
# Check BIND is running
podman ps | grep bind9-demo

# Check zone exists
./dns zone list

# Check zone file exists
ls -la zones/db.myzone.local
```

---

## 🎉 What You Can Do Now

```bash
# Manage multiple domains!
./dns zone add client1.com 192.168.1.17
./dns add www 10.10.10.10 --zone client1.com

./dns zone add client2.com 192.168.1.17
./dns add www 10.10.10.20 --zone client2.com

./dns zone add internal.local 192.168.1.17
./dns add intranet 10.10.10.30 --zone internal.local

# Each zone is independent!
./dns list --zone client1.com
./dns list --zone client2.com
./dns list --zone internal.local
```

---

**This completes full multi-zone DNS management!** 🚀
