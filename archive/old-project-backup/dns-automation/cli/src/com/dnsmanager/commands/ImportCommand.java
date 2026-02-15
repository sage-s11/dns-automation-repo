package com.dnsmanager.commands;

import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.services.*;
import com.dnsmanager.services.CSVParser.*;
import com.dnsmanager.services.EnterpriseNetworkValidator.*;
import java.util.*;

/**
 * Complete CSV import with validation
 */
public class ImportCommand {
    
    public static void main(String[] args) {
        
        if (args.length < 1) {
            System.out.println("Usage: dns-import <csv-file> [--validate-network]");
            System.out.println();
            System.out.println("Options:");
            System.out.println("  --validate-network    Check if IPs are active on network");
            System.out.println("  --skip-conflicts      Auto-skip conflicting IPs");
            System.out.println("  --dry-run            Preview only, don't create records");
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
            }
            System.out.println();
            
            // Step 2: Rules validation
            System.out.println("📋 Step 2: Rules validation...");
            RuleBasedValidator ruleValidator = new RuleBasedValidator();
            List<CSVRecord> validRecords = new ArrayList<>();
            List<CSVRecord> invalidRecords = new ArrayList<>();
            
            for (CSVRecord record : parsed.records) {
                RuleBasedValidator.ValidationResult result = 
                    ruleValidator.validateRecord(record.hostname, record.ip, record.type);
                
                if (result.isValid) {
                    validRecords.add(record);
                } else {
                    invalidRecords.add(record);
                    System.out.println("  ❌ " + record.hostname + ": " + result.reason);
                }
            }
            
            System.out.println("✅ " + validRecords.size() + " records passed rules");
            System.out.println("❌ " + invalidRecords.size() + " records failed");
            System.out.println();
            
            // Step 3: Network validation (optional)
            Map<String, IPValidationResult> networkResults = new HashMap<>();
            List<CSVRecord> activeIPRecords = new ArrayList<>();
            
            if (validateNetwork && !validRecords.isEmpty()) {
                System.out.println("🔍 Step 3: Network validation...");
                
                // Collect unique IPs
                List<String> ipsToCheck = new ArrayList<>();
                for (CSVRecord record : validRecords) {
                    if (record.type.equals("A") && !ipsToCheck.contains(record.ip)) {
                        ipsToCheck.add(record.ip);
                    }
                }
                
                // Validate in parallel
                EnterpriseNetworkValidator netValidator = new EnterpriseNetworkValidator();
                networkResults = netValidator.validateIPs(ipsToCheck);
                
                // Flag active IPs
                for (CSVRecord record : validRecords) {
                    IPValidationResult netResult = networkResults.get(record.ip);
                    if (netResult != null && netResult.isActive) {
                        activeIPRecords.add(record);
                        System.out.println("  ⚠️  " + record.ip + " is ACTIVE (" + 
                            netResult.detectionMethod + ")");
                    }
                }
                
                System.out.println();
            }
            
            // Step 4: Database conflict check
            System.out.println("💾 Step 4: Database conflict check...");
            ConflictChecker conflictChecker = new ConflictChecker();
            List<CSVRecord> conflictRecords = new ArrayList<>();
            
            for (CSVRecord record : validRecords) {
                String conflict = conflictChecker.checkIPConflict(record.ip);
                if (conflict != null) {
                    conflictRecords.add(record);
                    System.out.println("  ⚠️  " + record.ip + " already used by: " + conflict);
                }
            }
            System.out.println();
            
            // Step 5: Categorize
            List<CSVRecord> safeRecords = new ArrayList<>();
            for (CSVRecord record : validRecords) {
                boolean isActive = networkResults.containsKey(record.ip) && 
                                 networkResults.get(record.ip).isActive;
                boolean hasConflict = conflictRecords.contains(record);
                
                if (!isActive && !hasConflict) {
                    safeRecords.add(record);
                }
            }
            
            // Step 6: Summary
            System.out.println("=".repeat(70));
            System.out.println("📊 IMPORT SUMMARY");
            System.out.println("=".repeat(70));
            System.out.println();
            
            System.out.println("Total records in CSV: " + parsed.totalLines);
            System.out.println("  ✅ Safe to create:  " + safeRecords.size());
            System.out.println("  ⚠️  Active IPs:      " + activeIPRecords.size());
            System.out.println("  ⚠️  DB conflicts:    " + conflictRecords.size());
            System.out.println("  ❌ Invalid format:  " + invalidRecords.size());
            System.out.println();
            
            if (safeRecords.isEmpty()) {
                System.out.println("❌ No records to import!");
                return;
            }
            
            // Step 7: Preview
            System.out.println("✅ SAFE TO CREATE (" + safeRecords.size() + " records):");
            System.out.println("-".repeat(70));
            for (CSVRecord record : safeRecords.subList(0, Math.min(5, safeRecords.size()))) {
                System.out.println("  " + record.hostname + " → " + record.ip + 
                    " (" + record.zone + ")");
            }
            if (safeRecords.size() > 5) {
                System.out.println("  ... and " + (safeRecords.size() - 5) + " more");
            }
            System.out.println();
            
            if (!activeIPRecords.isEmpty()) {
                System.out.println("⚠️  WARNING: ACTIVE IPs DETECTED (" + activeIPRecords.size() + "):");
                System.out.println("-".repeat(70));
                for (CSVRecord record : activeIPRecords.subList(0, Math.min(3, activeIPRecords.size()))) {
                    IPValidationResult net = networkResults.get(record.ip);
                    System.out.println("  " + record.hostname + " → " + record.ip + 
                        " [" + net.detectionMethod + "]");
                }
                System.out.println("  These will be SKIPPED unless you approve manually.");
                System.out.println();
            }
            
            if (dryRun) {
                System.out.println("🔍 DRY-RUN MODE: No records created");
                System.out.println("   Remove --dry-run to execute import");
                return;
            }
            
            // Step 8: User confirmation
            System.out.print("Proceed with creating " + safeRecords.size() + " records? [y/N]: ");
            Scanner scanner = new Scanner(System.in);
            String response = scanner.nextLine().trim().toLowerCase();
            
            if (!response.equals("y") && !response.equals("yes")) {
                System.out.println("❌ Import cancelled");
                return;
            }
            
            // Step 9: Execute (TODO: integrate with DnsRecordServiceDB)
            System.out.println();
            System.out.println("🚀 Creating DNS records...");
            System.out.println("TODO: Integration with DnsRecordServiceDB");
            System.out.println("✅ Import would create " + safeRecords.size() + " records");
            
            // Cleanup
            DatabaseConfig.close();
            
        } catch (Exception e) {
            System.out.println("❌ Import failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
