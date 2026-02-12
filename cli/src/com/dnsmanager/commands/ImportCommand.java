package com.dnsmanager.commands;

import com.dnsmanager.services.BatchImportService;
import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.services.*;
import com.dnsmanager.services.CSVParser.*;
import com.dnsmanager.services.EnterpriseNetworkValidator.*;
import com.dnsmanager.models.Zone;
import com.dnsmanager.models.DnsRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;

/**
 * Complete CSV import with validation and execution
 * Supports: A, CNAME, MX, TXT records
 */
public class ImportCommand {
    
    public static void main(String[] args) {
        
        if (args.length < 1) {
            System.out.println("Usage: dns-import <csv-file> [options]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --validate-network    Check if IPs are active on network (A records only)");
            System.out.println("  --skip-conflicts      Auto-skip conflicting IPs");
            System.out.println("  --dry-run            Preview only, don't create records");
            System.out.println();
            System.out.println("Supported record types:");
            System.out.println("  A       - IPv4 address");
            System.out.println("  CNAME   - Canonical name (alias)");
            System.out.println("  MX      - Mail exchange (requires priority)");
            System.out.println("  TXT     - Text record");
            return;
        }
        
        String csvFile = args[0];
        boolean validateNetwork = Arrays.asList(args).contains("--validate-network");
        boolean skipConflicts = Arrays.asList(args).contains("--skip-conflicts");
        boolean dryRun = Arrays.asList(args).contains("--dry-run");
        
        System.out.println("🚀 DNS Import Tool");
        System.out.println("=".repeat(70));
        System.out.println("File: " + csvFile);
        System.out.println("Network validation: " + (validateNetwork ? "enabled" : "disabled"));
        System.out.println("Mode: " + (dryRun ? "DRY-RUN (preview only)" : "LIVE"));
        System.out.println();
        
        Scanner scanner = new Scanner(System.in);
        
        try {
            // Initialize database
            DatabaseConfig.initialize();
            
            // Step 1: Parse CSV
            System.out.println("📄 Step 1: Parsing CSV...");
            CSVParser parser = new CSVParser();
            ParseResult parsed = parser.parseFile(csvFile);
            
            System.out.println("✅ Parsed " + parsed.validRecords + " records");
            if (parsed.errors.size() > 0) {
                System.out.println("⚠️  " + parsed.errors.size() + " parse errors");
                for (String error : parsed.errors) {
                    System.out.println("  " + error);
                }
            }
            System.out.println();
            
            // Step 2: Rules validation
            System.out.println("📋 Step 2: Rules validation...");
            RuleBasedValidator ruleValidator = new RuleBasedValidator();
            List<CSVRecord> validRecords = new ArrayList<>();
            List<CSVRecord> invalidRecords = new ArrayList<>();
            
            for (CSVRecord record : parsed.records) {
                // Pass priority to validator for MX records
                RuleBasedValidator.ValidationResult result = 
                    ruleValidator.validateRecord(record.hostname, record.ip, record.type, record.priority);
                
                if (result.isValid) {
                    validRecords.add(record);
                } else {
                    invalidRecords.add(record);
                    System.out.println("  ❌ " + record.hostname + " (" + record.type + "): " + result.reason);
                }
            }
            
            System.out.println("✅ " + validRecords.size() + " records passed rules");
            System.out.println("❌ " + invalidRecords.size() + " records failed");
            System.out.println();
            
            // Step 3: Network validation (only for A records)
            Map<String, IPValidationResult> networkResults = new HashMap<>();
            List<CSVRecord> activeIPRecords = new ArrayList<>();
            List<CSVRecord> safeRecords = new ArrayList<>();
            
            if (validateNetwork && !validRecords.isEmpty()) {
                System.out.println("🔍 Step 3: Network validation...");
                
                // Collect unique IPs from A records only
                List<String> ipsToCheck = new ArrayList<>();
                for (CSVRecord record : validRecords) {
                    if (record.type.equals("A") && !ipsToCheck.contains(record.ip)) {
                        ipsToCheck.add(record.ip);
                    }
                }
                
                if (ipsToCheck.isEmpty()) {
                    System.out.println("ℹ️  No A records to validate (only CNAME/MX/TXT)");
                    safeRecords.addAll(validRecords);
                } else {
                    // Validate in parallel
                    EnterpriseNetworkValidator netValidator = new EnterpriseNetworkValidator();
                    networkResults = netValidator.validateIPs(ipsToCheck);
                    
                    // Categorize records
                    for (CSVRecord record : validRecords) {
                        // Non-A records are always safe (no network check needed)
                        if (!record.type.equals("A")) {
                            safeRecords.add(record);
                            continue;
                        }
                        
                        // Check A records
                        IPValidationResult netResult = networkResults.get(record.ip);
                        if (netResult != null && netResult.isActive) {
                            activeIPRecords.add(record);
                            System.out.println("  ⚠️  " + record.ip + " is ACTIVE (" + netResult.detectionMethod + ")");
                        } else {
                            safeRecords.add(record);
                        }
                    }
                }
            } else {
                // No network validation - all valid records are safe
                safeRecords.addAll(validRecords);
            }
            
            if (!validateNetwork) {
                System.out.println("🔍 Step 3: Network validation... SKIPPED");
            }
            System.out.println();
            
            // Step 4: Database conflict check
            System.out.println("💾 Step 4: Database conflict check...");
            ConflictChecker conflictChecker = new ConflictChecker();
            ZoneServiceDB zoneService = new ZoneServiceDB();
            
            List<CSVRecord> conflictRecords = new ArrayList<>();
            List<CSVRecord> readyToCreate = new ArrayList<>();
            
            for (CSVRecord record : safeRecords) {
                // Get zone ID
                Zone zone = zoneService.getZone(record.zone);
                if (zone == null) {
                    // Zone doesn't exist - will be created during import
                    readyToCreate.add(record);
                    continue;
                }
                
                // Check for hostname conflicts
                if (conflictChecker.hostnameExists(record.hostname, zone.getId())) {
                    conflictRecords.add(record);
                } else {
                    readyToCreate.add(record);
                }
            }
            
            System.out.println();
            
            // Summary
            System.out.println("=".repeat(70));
            System.out.println("📊 IMPORT SUMMARY");
            System.out.println("=".repeat(70));
            System.out.println();
            
            System.out.println("Total records in CSV: " + (parsed.validRecords + parsed.invalidRecords));
            System.out.println("  ✅ Safe to create:  " + readyToCreate.size());
            if (activeIPRecords.size() > 0) {
                System.out.println("  ⚠️  Active IPs:      " + activeIPRecords.size());
            }
            if (conflictRecords.size() > 0) {
                System.out.println("  ⚠️  DB conflicts:    " + conflictRecords.size());
            }
            if (invalidRecords.size() > 0) {
                System.out.println("  ❌ Invalid format:  " + invalidRecords.size());
            }
            System.out.println();
            
            // Show records by type
            Map<String, Integer> typeCount = new HashMap<>();
            for (CSVRecord record : readyToCreate) {
                typeCount.put(record.type, typeCount.getOrDefault(record.type, 0) + 1);
            }
            
            if (!typeCount.isEmpty()) {
                System.out.println("📋 Records by type:");
                typeCount.forEach((type, count) -> 
                    System.out.println("  " + type + ": " + count + " record(s)"));
                System.out.println();
            }
            
            // Show safe records
            if (!readyToCreate.isEmpty()) {
                System.out.println("✅ SAFE TO CREATE (" + readyToCreate.size() + " records):");
                System.out.println("-".repeat(70));
                for (CSVRecord record : readyToCreate) {
                    String display = "  " + record.hostname + " → " + record.ip + 
                        " (" + record.type;
                    if (record.priority != null) {
                        display += ", priority: " + record.priority;
                    }
                    display += ") [" + record.zone + "]";
                    System.out.println(display);
                }
                System.out.println();
            }
            
            // Show active IPs
            if (!activeIPRecords.isEmpty()) {
                System.out.println("⚠️  WARNING: ACTIVE IPs DETECTED (" + activeIPRecords.size() + "):");
                System.out.println("-".repeat(70));
                for (CSVRecord record : activeIPRecords) {
                    IPValidationResult netResult = networkResults.get(record.ip);
                    System.out.println("  " + record.hostname + " → " + record.ip + 
                        " [" + netResult.detectionMethod + "]");
                }
                System.out.println("  These will be SKIPPED unless you approve manually.");
                System.out.println();
            }
            
            // Show conflicts
            if (!conflictRecords.isEmpty()) {
                System.out.println("⚠️  DATABASE CONFLICTS (" + conflictRecords.size() + "):");
                System.out.println("-".repeat(70));
                for (CSVRecord record : conflictRecords) {
                    System.out.println("  " + record.hostname + " (" + record.type + 
                        ") already exists in " + record.zone);
                }
                System.out.println();
            }
            
            if (readyToCreate.isEmpty()) {
                System.out.println("ℹ️  No records to create. Check validation errors above.");
                return;
            }
            
            // Confirmation
            if (dryRun) {
                System.out.println("🔍 DRY-RUN MODE: No records will be created");
                return;
            }
            
            System.out.print("\nProceed with creating " + readyToCreate.size() + " records? [y/N]: ");
            String response = scanner.nextLine().trim().toLowerCase();
            
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("❌ Import cancelled");
                return;
            }

            // Create records
            System.out.println("\n🚀 Creating DNS records...");
            ZoneServiceDB zoneService2 = new ZoneServiceDB();

            int created = 0;
            int failed = 0;
            long startTime = System.currentTimeMillis();

            for (CSVRecord record : readyToCreate) {
                try {
                    // Get or create zone
                    Zone zone = zoneService2.getZone(record.zone);
                    if (zone == null) {
                        zone = zoneService2.createZone(record.zone, "10.10.10.1");
                    }

                    // Insert directly into database
                    String sql;
                    if (record.priority != null) {
                        // MX, SRV records with priority
                        sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl, priority) " +
                                "VALUES (?, ?, ?, ?, ?, ?)";
                    } else {
                        // A, CNAME, TXT records without priority
                        sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl) " +
                                "VALUES (?, ?, ?, ?, ?)";
                    }

                    try (Connection conn = DatabaseConfig.getDataSource().getConnection();
                         PreparedStatement stmt = conn.prepareStatement(sql)) {

                        stmt.setInt(1, zone.getId());
                        stmt.setString(2, record.hostname);
                        stmt.setString(3, record.type);
                        stmt.setString(4, record.ip);
                        stmt.setInt(5, record.ttl);

                        if (record.priority != null) {
                            stmt.setInt(6, record.priority);
                        }

                        stmt.executeUpdate();
                    }
                    
                    String display = "  ✅ " + record.hostname + " → " + record.ip + 
                        " (" + record.type;
                    if (record.priority != null) {
                        display += ", priority: " + record.priority;
                    }
                    display += ")";
                    System.out.println(display);
                    created++;
                    
                } catch (Exception e) {
                    System.out.println("  ❌ " + record.hostname + ": " + e.getMessage());
                    failed++;
                }
            }
            
            long dbTime = System.currentTimeMillis() - startTime;
            
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("📊 IMPORT COMPLETE");
            System.out.println("=".repeat(70));
            System.out.println("✅ Created: " + created);
            System.out.println("❌ Failed:  " + failed);
            System.out.println("⏱️  Database time: " + (dbTime / 1000.0) + "s");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                DatabaseConfig.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
