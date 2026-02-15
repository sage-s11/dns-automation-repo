# Multi-Zone Support - Quick Reference

## 📦 What You Have

**10 New Files:**
1. Zone.java - Zone data model
2. BindConfigService.java - Manages named.conf  
3. ZoneManagerService.java - Core zone logic
4. ZoneAddCommand.java - Create zones
5. ZoneListCommand.java - List zones
6. ZoneDeleteCommand.java - Delete zones
7. ZoneShowCommand.java - Show zone details
8. install-multizone.sh - Installation script
9. INSTALLATION.md - Detailed guide
10. DNSMANAGER-UPDATES.md - Code changes needed

---

## 🚀 Installation (3 Steps)

### Step 1: Download & Extract

```bash
cd ~/projects/dns-automation

# All files should be in Downloads or a multizone folder
# Move them to project root for installation
```

### Step 2: Run Installation Script

```bash
chmod +x install-multizone.sh
./install-multizone.sh
```

### Step 3: Update DnsManager.java Manually

```bash
nano cli/src/com/dnsmanager/DnsManager.java
```

Follow instructions in `DNSMANAGER-UPDATES.md`:
1. Add import for zonecommands
2. Initialize zone services
3. Add zone command routing  
4. Update usage text

Then recompile:
```bash
cd cli/src
javac com/dnsmanager/DnsManager.java
cd ../..
```

---

## 🎯 New Commands

### Zone Management

```bash
# List all zones
./dns zone list

# Create new zone
./dns zone add example.com 192.168.1.17

# Show zone details
./dns zone show example.com

# Delete zone
./dns zone delete example.com

# Delete without confirmation
./dns zone delete example.com --force
```

---

## 🧪 Test Workflow

```bash
# 1. List current zones
./dns zone list

# 2. Create test zone
./dns zone add testzone.local 192.168.1.17

# 3. Verify zone was created
ls -la zones/db.testzone.local
cat zones/db.testzone.local

# 4. Check BIND config
cat config/named.conf | grep testzone

# 5. Test DNS query for nameserver
dig @127.0.0.1 -p 1053 ns1.testzone.local +short
# Should return: 192.168.1.17

# 6. Show zone stats
./dns zone show testzone.local

# 7. Clean up (optional)
./dns zone delete testzone.local --force
```

---

## 📂 What Gets Created

When you create a zone:

**1. Zone File:** `zones/db.example.com`
```dns
$TTL 86400
@   IN  SOA ns1.example.com. admin.example.com. (
        2026020701 ; Serial
        3600       ; Refresh
        1800       ; Retry
        604800     ; Expire
        86400 )    ; Minimum

@   IN  NS  ns1.example.com.
ns1 IN  A   192.168.1.17
```

**2. BIND Config Entry:** `config/named.conf`
```
zone "example.com" {
    type master;
    file "/zones/db.example.com";
    allow-update { none; };
};
```

**3. BIND Automatically Reloads**

---

## 🔍 How It Works

```
./dns zone add example.com 192.168.1.17
         ↓
    ZoneAddCommand
         ↓
  ZoneManagerService
    ├→ Creates zone file (zones/db.example.com)
    ├→ Sets permissions (644)
    └→ Calls BindConfigService
         ↓
   BindConfigService
    ├→ Reads named.conf
    ├→ Adds zone block
    ├→ Writes named.conf
    └→ Runs: rndc reconfig
         ↓
      BIND9 Container
    ├→ Reads new config
    └→ Loads new zone
         ↓
        ✅ Done!
```

---

## ⚠️ Troubleshooting

### "cannot find symbol" During Compilation

```bash
# Recompile in correct order
cd cli/src
javac com/dnsmanager/models/*.java
javac com/dnsmanager/services/*.java
javac com/dnsmanager/zonecommands/*.java
javac com/dnsmanager/DnsManager.java
```

### "Zone already exists"

```bash
# Check what zones exist
./dns zone list

# Check zone files
ls -la zones/

# Check BIND config
cat config/named.conf
```

### "rndc reconfig failed"

```bash
# Check BIND is running
sudo systemctl status dns-server

# Check container
podman ps | grep bind9-demo

# Restart if needed
sudo systemctl restart dns-server
```

---

## 📝 Next Phase: --zone Flag for Records

After zone management works, we'll update existing commands:

```bash
# Current (hardcoded to examplenv.demo)
./dns add www 10.10.10.10

# Future (specify zone)
./dns add www 10.10.10.10 --zone example.com
./dns add api 10.10.10.20 --zone mysite.org

# List records from specific zone
./dns list --zone example.com
```

This requires updating:
- AddCommand.java
- UpdateCommand.java
- SetCommand.java
- DeleteCommand.java
- ListCommand.java

---

## 🎯 Architecture

```
Before (Single Zone):
dns-automation/
└── zones/
    └── db.examplenv.demo (hardcoded)

After (Multi-Zone):
dns-automation/
└── zones/
    ├── db.examplenv.demo
    ├── db.example.com
    ├── db.testzone.local
    └── db.mycompany.org
```

Each zone is:
- ✅ Independent
- ✅ Managed separately
- ✅ Auto-configured in BIND
- ✅ Backed up individually

---

## ✅ Success Criteria

Multi-zone support is working when:

- [ ] `./dns zone list` shows zones
- [ ] Can create new zone
- [ ] New zone appears in named.conf
- [ ] Zone file created with correct format
- [ ] DNS query for ns1.ZONE returns correct IP
- [ ] Can delete zone (with confirmation)
- [ ] BIND recognizes all zones

---

## 📞 Questions?

If stuck, check:
1. INSTALLATION.md - Detailed steps
2. DNSMANAGER-UPDATES.md - Code changes
3. Error messages - Usually tell you what's wrong

Common issues:
- Wrong directory when running commands
- Forgot to recompile after changes
- BIND container not running
- Permissions on files

---

**Ready to install? Follow INSTALLATION.md step-by-step!** 🚀
