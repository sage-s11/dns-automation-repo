# DnsManager.java Updates for Multi-Zone Support

## Add to Imports Section (Top of File)

```java
import com.dnsmanager.zonecommands.*;
```

## Add to initializeServices() Method

```java
private static BindConfigService bindConfigService;
private static ZoneManagerService zoneManager;

private static void initializeServices() throws IOException {
    // Existing services
    zoneService = new ZoneFileService(ZONE_NAME);
    serverService = new DnsServerService(CONTAINER_NAME, ZONE_NAME, RNDC_PORT);
    backupService = new BackupService(backupDir, MAX_BACKUPS);
    
    // NEW: Zone management services
    bindConfigService = new BindConfigService("config/named.conf");
    zoneManager = new ZoneManagerService("zones", bindConfigService);
}
```

## Add to main() Method (Before Existing Command Checks)

```java
public static void main(String[] args) {
    if (args.length == 0) {
        printUsage();
        System.exit(1);
    }

    String command = args[0].toLowerCase();

    try {
        initializeServices();

        // NEW: Zone management commands
        if (command.equals("zone")) {
            if (args.length < 2) {
                System.err.println("Usage: ./dns zone <add|list|delete|show> [options]");
                System.err.println("");
                System.err.println("Commands:");
                System.err.println("  zone add <domain> <ns-ip>  - Create new zone");
                System.err.println("  zone list                  - List all zones");
                System.err.println("  zone delete <domain>       - Delete zone");
                System.err.println("  zone show <domain>         - Show zone details");
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
                    System.err.println("Available: add, list, delete, show");
                    System.exit(1);
            }
            return;  // Exit after handling zone command
        }

        // EXISTING commands continue below...
        switch (command) {
            case "add":
                new AddCommand(zoneService, serverService, backupService).execute(
                    Arrays.copyOfRange(args, 1, args.length));
                break;
            // ... rest of existing commands ...
        }

    } catch (Exception e) {
        System.err.println("❌ Error: " + e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
}
```

## Update printUsage() Method

```java
private static void printUsage() {
    System.out.println("DNS Management Tool");
    System.out.println("");
    System.out.println("Zone Management:");
    System.out.println("  ./dns zone add <domain> <ns-ip>    - Create new zone");
    System.out.println("  ./dns zone list                    - List all zones");
    System.out.println("  ./dns zone delete <domain>         - Delete zone");
    System.out.println("  ./dns zone show <domain>           - Show zone details");
    System.out.println("");
    System.out.println("Record Management:");
    System.out.println("  ./dns add <hostname> <ip>          - Add DNS record");
    System.out.println("  ./dns update <hostname> <ip>       - Update DNS record");
    System.out.println("  ./dns set <hostname> <ip>          - Add or update record");
    System.out.println("  ./dns delete <hostname>            - Delete DNS record");
    System.out.println("  ./dns list                         - List all records");
    System.out.println("");
    System.out.println("Server Operations:");
    System.out.println("  ./dns reload                       - Reload DNS server");
    System.out.println("  ./dns status                       - Check server status");
    System.out.println("  ./dns backup                       - Create manual backup");
}
```

## Complete Test After Updates

```bash
# Recompile
cd ~/projects/dns-automation/cli/src
javac com/dnsmanager/DnsManager.java
cd ../..

# Test zone commands
./dns zone list
./dns zone add example.com 192.168.1.17
./dns zone show example.com
./dns zone list

# Test DNS query
dig @127.0.0.1 -p 1053 ns1.example.com +short
```
