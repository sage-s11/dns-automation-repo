package com.dnsmanager.commands;

import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.services.CleanupService;
import com.dnsmanager.services.DnsRecordServiceDB;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * DNS Record Deletion Command
 * 
 * Usage:
 *   Delete by hostname: --hostname web-01
 *   Delete by pattern:  --pattern "test-*"
 *   Delete by IP:       --ip 10.88.88.1
 *   Delete by zone:     --zone test.local
 *   Cleanup tests:      --cleanup-tests
 *   From CSV:           --from-csv file.csv
 *   Dry-run:            --dry-run (preview only)
 */
public class DeleteCommand {
    
    private static final Scanner scanner = new Scanner(System.in);
    private static CleanupService cleanupService;
    private static boolean dryRun = false;
    private static boolean skipValidation = false;  // --force flag
    
    public static void main(String[] args) {
        System.out.println("🗑️  DNS Deletion Tool");
        System.out.println("======================================================================");
        
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        try {
            // Parse arguments
            String hostname = null;
            String pattern = null;
            String ip = null;
            String zone = null;
            String csvFile = null;
            boolean cleanupTests = false;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--hostname":
                        hostname = args[++i];
                        break;
                    case "--pattern":
                        pattern = args[++i];
                        break;
                    case "--ip":
                        ip = args[++i];
                        break;
                    case "--zone":
                        zone = args[++i];
                        break;
                    case "--from-csv":
                        csvFile = args[++i];
                        break;
                    case "--cleanup-tests":
                        cleanupTests = true;
                        break;
                    case "--dry-run":
                        dryRun = true;
                        break;
                    case "--force":
                        skipValidation = true;
                        break;
                    case "--help":
                        printUsage();
                        System.exit(0);
                        break;
                }
            }
            
            // Initialize services
            DatabaseConfig.initialize();
            cleanupService = new CleanupService();
            
            System.out.println("Mode: " + (dryRun ? "DRY-RUN (preview only)" : "LIVE"));
            if (skipValidation) {
                System.out.println("⚠️  WARNING: Network validation DISABLED (--force)");
            } else {
                System.out.println("Network validation: ENABLED (checking IP status)");
            }
            System.out.println();
            
            // Execute deletion based on mode
            if (hostname != null) {
                deleteByHostname(hostname);
            } else if (pattern != null) {
                deleteByPattern(pattern);
            } else if (ip != null) {
                deleteByIP(ip);
            } else if (zone != null) {
                deleteByZone(zone);
            } else if (csvFile != null) {
                deleteFromCSV(csvFile);
            } else if (cleanupTests) {
                cleanupTestData();
            } else {
                System.err.println("❌ No deletion criteria specified!");
                printUsage();
                System.exit(1);
            }
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            try {
                DatabaseConfig.close();
                System.out.println("✅ Database connection closed");
            } catch (Exception e) {
                System.err.println("⚠️  Error closing database: " + e.getMessage());
            }
        }
    }
    
    /**
     * Delete by exact hostname
     */
    private static void deleteByHostname(String hostname) throws SQLException {
        System.out.println("🔍 Finding records for hostname: " + hostname);
        
        List<Map<String, Object>> records = cleanupService.findByHostname(hostname);
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No records found for hostname: " + hostname);
            return;
        }
        
        System.out.println("\n📋 Found " + records.size() + " record(s):");
        printRecords(records);
        
        // Validate network status if not in dry-run and not forced
        if (!dryRun && !skipValidation) {
            System.out.println("\n🔍 Validating IP status...");
            CleanupService.DeletionValidationResult validation = 
                cleanupService.validateForDeletion(records);
            
            if (validation.hasBlockedRecords()) {
                System.out.println("\n❌ BLOCKED: " + validation.getBlockedCount() + 
                    " record(s) have active IPs:");
                printBlockedRecords(validation.activeIPs, validation.blockedReasons);
                System.out.println("\n💡 Use --force to override (not recommended)");
                return;
            }
            
            System.out.println("✅ All IPs are not responding - safe to delete");
        }
        
        if (!dryRun && confirmDeletion(records.size())) {
            int deleted = cleanupService.deleteByHostname(hostname, skipValidation);
            System.out.println("✅ Deleted " + deleted + " record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Deletion cancelled");
        }
    }
    
    /**
     * Print blocked records
     */
    private static void printBlockedRecords(List<Map<String, Object>> records,
                                           Map<String, String> reasons) {
        for (Map<String, Object> record : records) {
            String ip = record.get("value").toString();
            String reason = reasons.getOrDefault(ip, "Unknown");
            System.out.printf("  ❌ %s → %s [%s]%n",
                record.get("hostname"), ip, reason);
        }
    }
    
    /**
     * Delete by pattern (wildcard)
     */
    private static void deleteByPattern(String pattern) throws SQLException {
        System.out.println("🔍 Finding records matching pattern: " + pattern);
        
        List<Map<String, Object>> records = cleanupService.findByPattern(pattern);
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No records found matching pattern: " + pattern);
            return;
        }
        
        System.out.println("\n📋 Found " + records.size() + " record(s):");
        printRecords(records);
        
        if (!dryRun && confirmDeletion(records.size())) {
            int deleted = cleanupService.deleteByPattern(pattern, skipValidation);
            System.out.println("✅ Deleted " + deleted + " record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Deletion cancelled");
        }
    }
    
    /**
     * Delete by IP address
     */
    private static void deleteByIP(String ip) throws SQLException {
        System.out.println("🔍 Finding records for IP: " + ip);
        
        List<Map<String, Object>> records = cleanupService.findByIP(ip);
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No records found for IP: " + ip);
            return;
        }
        
        System.out.println("\n📋 Found " + records.size() + " record(s):");
        printRecords(records);
        
        if (!dryRun && confirmDeletion(records.size())) {
            int deleted = cleanupService.deleteByIP(ip, skipValidation);
            System.out.println("✅ Deleted " + deleted + " record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Deletion cancelled");
        }
    }
    
    /**
     * Delete by zone
     */
    private static void deleteByZone(String zoneName) throws SQLException {
        System.out.println("🔍 Finding records in zone: " + zoneName);
        
        List<Map<String, Object>> records = cleanupService.findByZone(zoneName);
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No records found in zone: " + zoneName);
            return;
        }
        
        System.out.println("\n📋 Found " + records.size() + " record(s):");
        printRecords(records);
        
        System.out.println("\n⚠️  WARNING: This will delete ALL records in zone: " + zoneName);
        
        if (!dryRun && confirmDeletion(records.size())) {
            int deleted = cleanupService.deleteByZone(zoneName, skipValidation);
            System.out.println("✅ Deleted " + deleted + " record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Deletion cancelled");
        }
    }
    
    /**
     * Delete from CSV file
     */
    private static void deleteFromCSV(String csvFile) throws SQLException {
        System.out.println("🔍 Loading deletion list from: " + csvFile);
        
        List<Map<String, Object>> records = cleanupService.findFromCSV(csvFile);
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No matching records found from CSV");
            return;
        }
        
        System.out.println("\n📋 Found " + records.size() + " record(s) to delete:");
        printRecords(records);
        
        if (!dryRun && confirmDeletion(records.size())) {
            int deleted = cleanupService.deleteFromCSV(csvFile, skipValidation);
            System.out.println("✅ Deleted " + deleted + " record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Deletion cancelled");
        }
    }
    
    /**
     * Cleanup test data
     */
    private static void cleanupTestData() throws SQLException {
        System.out.println("🔍 Finding test data records...");
        
        // Find records with test patterns
        String[] testPatterns = {"test-%", "%-test", "web-server-%", "db-server-%", 
                                 "cache-server-%", "app-server-%", "api-gateway-%",
                                 "storage-node-%", "backup-server", "monitoring-server",
                                 "web-%", "db-%", "cache-%", "app-%", "api-%", 
                                 "lb-%", "storage-%", "backup-%", "monitor-%"};
        
        List<Map<String, Object>> allRecords = cleanupService.findTestData(testPatterns);
        
        if (allRecords.isEmpty()) {
            System.out.println("ℹ️  No test data found");
            return;
        }
        
        System.out.println("\n📋 Found " + allRecords.size() + " test record(s):");
        printRecords(allRecords);
        
        if (!dryRun && confirmDeletion(allRecords.size())) {
            int deleted = cleanupService.cleanupTestData(testPatterns, skipValidation);
            System.out.println("✅ Deleted " + deleted + " test record(s)");
        } else if (dryRun) {
            System.out.println("\n🔍 DRY-RUN MODE: No records deleted");
        } else {
            System.out.println("❌ Cleanup cancelled");
        }
    }
    
    /**
     * Print records in formatted table
     */
    private static void printRecords(List<Map<String, Object>> records) {
        System.out.println("----------------------------------------------------------------------");
        System.out.printf("%-25s %-20s %-15s %-15s%n", "HOSTNAME", "IP/VALUE", "TYPE", "ZONE");
        System.out.println("----------------------------------------------------------------------");
        
        int count = 0;
        for (Map<String, Object> record : records) {
            if (count < 20) {  // Show first 20
                System.out.printf("%-25s %-20s %-15s %-15s%n",
                    truncate(record.get("hostname").toString(), 25),
                    truncate(record.get("value").toString(), 20),
                    record.get("type"),
                    record.get("zone")
                );
            }
            count++;
        }
        
        if (count > 20) {
            System.out.println("... and " + (count - 20) + " more");
        }
        System.out.println("----------------------------------------------------------------------");
        System.out.println("Total: " + count + " record(s)");
    }
    
    /**
     * Confirm deletion with user
     */
    private static boolean confirmDeletion(int count) {
        System.out.print("\n⚠️  Delete " + count + " record(s)? Type 'yes' to confirm: ");
        String response = scanner.nextLine().trim();
        return response.equalsIgnoreCase("yes");
    }
    
    /**
     * Truncate string to max length
     */
    private static String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
    
    /**
     * Print usage instructions
     */
    private static void printUsage() {
        System.out.println("\nUsage: DeleteCommand [OPTIONS]");
        System.out.println("\nOptions:");
        System.out.println("  --hostname <name>    Delete records by exact hostname");
        System.out.println("  --pattern <pattern>  Delete records matching pattern (use * for wildcard)");
        System.out.println("  --ip <address>       Delete records by IP address");
        System.out.println("  --zone <name>        Delete all records in zone");
        System.out.println("  --from-csv <file>    Delete records listed in CSV file");
        System.out.println("  --cleanup-tests      Delete common test data patterns");
        System.out.println("  --dry-run            Preview deletions without executing");
        System.out.println("  --help               Show this help message");
        System.out.println("\nExamples:");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.DeleteCommand --hostname web-01");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.DeleteCommand --pattern \"test-*\" --dry-run");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.DeleteCommand --ip 10.88.88.1");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.DeleteCommand --zone test.local");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.DeleteCommand --cleanup-tests");
    }
}
