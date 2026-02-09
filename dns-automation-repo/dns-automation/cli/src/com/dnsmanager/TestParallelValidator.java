package com.dnsmanager;

import com.dnsmanager.services.*;
import com.dnsmanager.services.ParallelValidator.ValidationResultFull;
import com.dnsmanager.services.CSVParser;
import com.dnsmanager.services.CSVParser.CSVRecord;
import java.util.List;

public class TestParallelValidator {
    public static void main(String[] args) {
        
        System.out.println("⚡ Testing Parallel Validator with Network Checks\n");
        System.out.println("=".repeat(70));
        
        try {
            // Create test CSV with some IPs that might be active
            String testFile = "/tmp/dns-parallel-test.csv";
            CSVParser.createSample(testFile);
            
            // Parse CSV
            CSVParser parser = new CSVParser();
            CSVParser.ParseResult parsed = parser.parseFile(testFile);
            
            System.out.println("📊 Test Data:");
            System.out.println("   Records to validate: " + parsed.validRecords);
            System.out.println();
            
            // Create parallel validator
            ParallelValidator validator = new ParallelValidator();
            
            // Run validation with all features
            List<ValidationResultFull> results = validator.validateRecords(
                parsed.records,
                false,   // Use AI
                true    // Check network (ping)
            );
            
            // Display results
            System.out.println("📋 Validation Results:");
            System.out.println("=".repeat(70));
            
            int safe = 0, invalid = 0, activeIP = 0, review = 0;
            
            for (ValidationResultFull result : results) {
                String category = result.getCategory();
                
                System.out.print("  ");
                
                switch (category) {
                    case "safe":
                        System.out.print("✅ SAFE:        ");
                        safe++;
                        break;
                    case "invalid":
                        System.out.print("❌ INVALID:     ");
                        invalid++;
                        break;
                    case "warning_active_ip":
                        System.out.print("⚠️  ACTIVE IP:  ");
                        activeIP++;
                        break;
                    default:
                        System.out.print("⚠️  REVIEW:     ");
                        review++;
                }
                
                System.out.println(result.getSummary());
                
                // Show network status if checked
                if (result.networkStatus != null) {
                    System.out.println("                 Network: " + result.networkStatus);
                }
            }
            
            System.out.println();
            System.out.println("=".repeat(70));
            System.out.println("📊 Summary:");
            System.out.println("   ✅ Safe to create:      " + safe);
            System.out.println("   ❌ Invalid:             " + invalid);
            System.out.println("   ⚠️  Active IP warning:  " + activeIP);
            System.out.println("   ⚠️  Needs review:       " + review);
            System.out.println("=".repeat(70));
            
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
