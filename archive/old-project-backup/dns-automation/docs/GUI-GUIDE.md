# DNS Web GUI - Installation & Usage Guide

## 📥 Installation (5 minutes)

### Step 1: Download Files
Download these 3 files from outputs:
1. `install-gui.sh`
2. `app.py`
3. `index.html`

### Step 2: Install

```bash
cd ~/projects/dns-automation

# Make installer executable
chmod +x install-gui.sh

# Run installer
./install-gui.sh

# Create templates directory and copy HTML
mkdir -p gui/templates
cp app.py gui/
cp index.html gui/templates/

# Install Flask (if not done by installer)
pip3 install flask --break-system-packages
```

### Step 3: Fix Zone File Permissions (Important!)

```bash
# Fix permissions so BIND can read zone files
chmod 644 zones/db.examplenv.demo
chmod 644 zones/db.10.10.10
```

---

## 🚀 Usage

### Start the GUI

```bash
cd ~/projects/dns-automation/gui
python3 app.py
```

You'll see:
```
============================================================
DNS Management Web GUI
============================================================
Access at: http://127.0.0.1:8080
Press Ctrl+C to stop
============================================================
```

### Access in Browser

Open your browser and go to:
```
http://127.0.0.1:8080
```

---

## 🎨 Features

### 1. Dashboard
- **Statistics**: See total records, A records, NS records
- **Clean UI**: Modern, responsive design

### 2. Add Records
- Enter hostname (e.g., `test`, `app`, `mail`)
- Enter IP address (e.g., `10.10.10.50`)
- Click "Add Record"
- Automatically reloads zone

### 3. View Records
- Table view of all DNS records
- Shows hostname, type, and value
- Color-coded and easy to read

### 4. Delete Records
- Click "Delete" button next to any record
- Confirms before deleting
- Updates automatically

---

## 🔐 Security

### Current Setup (Localhost Only)
```
✅ Secure by default
✅ Only accessible from your laptop
✅ No network exposure
✅ No authentication needed (localhost = trusted)

Access:
✅ http://127.0.0.1:8080 (works)
❌ http://10.241.218.22:8080 (blocked)
```

**Phone/tablet CANNOT access this** - by design!

### To Add Network Access Later

Edit `gui/app.py`, change last line:

```python
# FROM (localhost only):
app.run(host='127.0.0.1', port=8080, debug=True)

# TO (network accessible - ADD AUTH FIRST!):
app.run(host='0.0.0.0', port=8080, debug=True)
```

**⚠️ Do NOT change to 0.0.0.0 without adding authentication first!**

---

## 🧪 Testing

### Test 1: Add a Record
1. Open http://127.0.0.1:8080
2. Enter:
   - Hostname: `gui-test`
   - IP: `10.10.10.199`
3. Click "Add Record"
4. Should see success message
5. Record appears in table

### Test 2: Verify DNS Works
```bash
# In terminal
dig @127.0.0.1 -p 1053 gui-test.examplenv.demo +short

# Should return: 10.10.10.199
```

### Test 3: Delete a Record
1. Find `gui-test` in table
2. Click "Delete"
3. Confirm deletion
4. Record disappears

### Test 4: From Phone (DNS Query Only)
```bash
# On phone (Termux)
dig @10.241.218.22 -p 1053 gui-test.examplenv.demo +short

# Should return: 10.10.10.199
```

**Note:** Phone CANNOT access the GUI (localhost only)

---

## 🐛 Troubleshooting

### Issue: "Connection Refused"
```bash
# Make sure app is running
cd ~/projects/dns-automation/gui
python3 app.py
```

### Issue: "Delete command not found"
```bash
# Install delete command first
cd ~/projects/dns-automation
# (Use the install-delete-command.sh script from earlier)
```

### Issue: "Permission denied" on zone files
```bash
# Fix zone file permissions
chmod 644 zones/db.examplenv.demo
```

### Issue: Changes don't appear
```bash
# Check BIND container is running
podman ps | grep bind9-demo

# If not running:
./bind/run.sh
```

### Issue: Flask not found
```bash
# Install Flask
pip3 install flask --break-system-packages
```

---

## 📊 Architecture

```
Browser (http://127.0.0.1:8080)
    ↓
Flask Web Server (gui/app.py)
    ↓
DNS CLI (./dns add/delete/list)
    ↓
Zone Files (zones/db.examplenv.demo)
    ↓
BIND9 Container (DNS Server)
```

---

## 🎯 Next Steps

After testing the GUI:

1. ✅ **Add Authentication** (if you want network access)
   - Implement HTTP Basic Auth
   - Change to `host='0.0.0.0'`
   - Access from phone with password

2. ✅ **Add Database** (next week)
   - PostgreSQL/SQLite
   - Audit logging
   - Better search/filter

3. ✅ **Add Infoblox-style Backup** (later)
   - Automatic SCP to backup server
   - Compression
   - Rotation

---

## 💡 Tips

- **Keep it simple**: Start with localhost, add features incrementally
- **Test often**: Try adding/deleting records frequently
- **Check logs**: Flask shows all requests in terminal
- **Zone permissions**: Always `chmod 644` zone files after changes

---

## 🎥 Demo Recording

Record your screen showing:
1. Open GUI in browser
2. Add a record via GUI
3. Query it from terminal
4. Delete it via GUI
5. Verify it's gone

**This is gold for your resume/portfolio!** 🏆
