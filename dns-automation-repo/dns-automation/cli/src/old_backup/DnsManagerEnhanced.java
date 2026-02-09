import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class DnsManagerEnhanced {
    private static final Path ZONE_FILE = Paths.get("zones/db.examplenv.demo");
    private static final String ZONE_NAME = "examplenv.demo";
    private static final String RELOAD_SCRIPT = "scripts/reload-zone.sh";
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3}\\.){3}\\d{1,3}\\b");
    
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        
        switch (args[0]) {
            case "list":
                listRecords();
                break;
            case "add":
                if (args.length < 3) {
                    System.err.println("Usage: add <hostname> <ip>");
                    return;
                }
                addRecord(args[1], args[2]);
                break;
            case "reload":
                reloadZone();
                break;
            case "status":
                checkStatus();
                break;
            case "validate":
                validateZone();
                break;
            case "backup":
                backupZone();
                break;
            default:
                usage();
        }
    }
    
    private static void usage() {
        System.out.println("DNS Manager - Enhanced CLI");
        System.out.println("\nUsage:");
        System.out.println("  list                    - List all DNS records");
        System.out.println("  add <hostname> <ip>     - Add/update a DNS record");
        System.out.println("  reload                  - Safely reload the zone");
        System.out.println("  validate                - Validate zone file syntax");
        System.out.println("  status                  - Check BIND server status");
        System.out.println("  backup                  - Create manual backup of zone file");
    }
    
    private static void listRecords() throws IOException {
        System.out.println("DNS Records for " + ZONE_NAME + ":");
        System.out.println("─".repeat(60));
        
        List<String> lines = Files.readAllLines(ZONE_FILE);
        boolean inRecords = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.contains(")") && !inRecords) {
                inRecords = true;
                continue;
            }
            if (inRecords && !line.isEmpty() && !line.startsWith(";")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    String hostname = parts[0];
                    String type = parts[parts.length - 2];
                    String value = parts[parts.length - 1];
                    System.out.printf("  %-20s %-6s %s\n", hostname, type, value);
                }
            }
        }
    }
    
    private static void addRecord(String hostname, String ip) throws Exception {
        if (!IPV4.matcher(ip).matches()) {
            throw new IllegalArgumentException("Invalid IP address: " + ip);
        }
        
        System.out.println("Adding record: " + hostname + " -> " + ip);
        
        List<String> lines = new ArrayList<>(Files.readAllLines(ZONE_FILE));
        boolean recordExists = false;
        boolean serialBumped = false;
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            
            if (line.contains("Serial") && !serialBumped) {
                lines.set(i, bumpSerial(line));
                serialBumped = true;
                System.out.println("Serial number incremented");
            }
            
            if (line.trim().startsWith(hostname + "\t") || line.trim().startsWith(hostname + " ")) {
                lines.set(i, String.format("%s\tIN\tA\t%s", hostname, ip));
                recordExists = true;
                System.out.println("Updated existing record");
            }
        }
        
        if (!recordExists) {
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains(")")) {
                    lines.add(i + 1, "");
                    lines.add(i + 2, String.format("%s\tIN\tA\t%s", hostname, ip));
                    System.out.println("Added new record");
                    break;
                }
            }
        }
        
        Path tempFile = Files.createTempFile("zone", ".tmp");
        Files.write(tempFile, lines);
        Files.move(tempFile, ZONE_FILE, StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("✓ Record saved to zone file");
        System.out.println("\nReloading zone...");
        reloadZone();
    }
    
    private static void reloadZone() throws Exception {
        System.out.println("Reloading zone: " + ZONE_NAME);
        
        Path scriptPath = Paths.get(RELOAD_SCRIPT);
        if (!Files.exists(scriptPath)) {
            System.err.println("Error: Reload script not found at " + RELOAD_SCRIPT);
            return;
        }
        
        ProcessBuilder pb = new ProcessBuilder("bash", RELOAD_SCRIPT, ZONE_FILE.toString(), ZONE_NAME);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            System.out.println("\n✓ Zone reloaded successfully!");
        } else {
            System.err.println("\n✗ Zone reload failed");
            throw new RuntimeException("Reload failed");
        }
    }
    
    private static void validateZone() throws Exception {
        System.out.println("Validating zone file: " + ZONE_FILE);
        
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", "bind9-demo",
            "named-checkzone", ZONE_NAME, "/zones/" + ZONE_FILE.getFileName()
        );
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            System.out.println("\n✓ Zone file is valid");
        } else {
            System.err.println("\n✗ Zone file validation failed");
        }
    }
    
    private static void checkStatus() throws Exception {
        System.out.println("Checking BIND server status...\n");
        
        ProcessBuilder pb = new ProcessBuilder(
            "podman", "exec", "bind9-demo",
            "rndc", "-p", "1953", "status"
        );
        pb.inheritIO();
        Process process = pb.start();
        process.waitFor();
    }
    
    private static void backupZone() throws IOException {
        Path backupDir = ZONE_FILE.getParent().resolve("backups");
        Files.createDirectories(backupDir);
        
        String timestamp = String.format("%tY%<tm%<td_%<tH%<tM%<tS", new Date());
        Path backupFile = backupDir.resolve(ZONE_FILE.getFileName() + "." + timestamp);
        
        Files.copy(ZONE_FILE, backupFile);
        System.out.println("✓ Backup created: " + backupFile);
    }
    
    private static String bumpSerial(String line) {
        String serialStr = line.replaceAll("\\D", "");
        long serial = Long.parseLong(serialStr);
        return line.replaceAll("\\d+", String.valueOf(serial + 1));
    }
}
