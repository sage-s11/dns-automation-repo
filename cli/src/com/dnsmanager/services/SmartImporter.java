package com.dnsmanager.services;


import com.dnsmanager.services.CSVParser.*;
import com.dnsmanager.services.AIValidator.*;
import java.util.*;

/**
 * Smart DNS Import with AI-powered validation
 * Implements 7-step hybrid workflow
 */
public class SmartImporter {
    
    private CSVParser csvParser;
    private AIValidator aiValidator;
    
    /**
     * Import result summary
     */
    public static class ImportResult {
        public List<CSVRecord> safeRecords = new ArrayList<>();
        public List<CSVRecord> conflictRecords = new ArrayList<>();
        public List<CSVRecord> invalidRecords = new ArrayList<>();
        public List<StaleRecommendation> staleRecommendations = new ArrayList<>();
        public List<CSVRecord> needsReview = new ArrayList<>();
        
        public int totalProcessed = 0;
        public int autoCreated = 0;
        public int skipped = 0;
        public int flaggedForReview = 0;
    }
    
    /**
     * Stale record recommendation
     */
    public static class StaleRecommendation {
        public String hostname;
        public String ip;
        public String reason;
        public double confidence;
        public String action;  // "safe_to_remove", "recommend_remove", "keep"
        
        @Override
        public String toString() {
            return String.format("%s (%s) - %s [%.0f%% confidence]",
                hostname, ip, action, confidence * 100);
        }
    }
    
    public SmartImporter() {
        this.csvParser = new CSVParser();
        this.aiValidator = new AIValidator();
    }
    
    /**
     * STEP 1: Accept and parse CSV
     * STEP 2: AI validation
     * STEP 3: Categorize records
     */
    public ImportResult analyzeCSV(String filepath) {
        ImportResult result = new ImportResult();
        
        System.out.println("🤖 Smart DNS Importer - AI-Powered Analysis");
        System.out.println("=".repeat(70));
        System.out.println();
        
        // STEP 1: Parse CSV
        System.out.println("Step 1: Parsing CSV file...");
        ParseResult parsed;
        try {
            parsed = csvParser.parseFile(filepath);
            System.out.println("✅ Parsed " + parsed.validRecords + " records");
            
            if (parsed.errors.size() > 0) {
                System.out.println("⚠️  " + parsed.errors.size() + " parse errors (will be skipped)");
            }
            System.out.println();
        } catch (Exception e) {
            System.out.println("❌ Failed to parse CSV: " + e.getMessage());
            return result;
        }
        
        // STEP 2 & 3: AI Validation & Categorization
        System.out.println("Step 2: AI Validation (analyzing patterns & validity)...");
        System.out.println();
        
        int count = 0;
        for (CSVRecord record : parsed.records) {
            count++;
            result.totalProcessed++;
            
            System.out.print(String.format("  [%d/%d] %s → %s ... ", 
                count, parsed.records.size(), record.hostname, record.ip));
            
            // Validate with AI
            ValidationResult validation = aiValidator.validateRecord(
                record.hostname, 
                record.ip, 
                record.zone
            );
            
            // Categorize based on AI analysis
            if (validation.category.equals("invalid")) {
                result.invalidRecords.add(record);
                result.skipped++;
                System.out.println("❌ INVALID (" + validation.reason + ")");
                
            } else if (validation.category.equals("safe") && validation.confidence > 0.85) {
                result.safeRecords.add(record);
                System.out.println("✅ SAFE (auto-create)");
                
            } else if (validation.category.equals("conflict")) {
                result.conflictRecords.add(record);
                result.flaggedForReview++;
                System.out.println("⚠️  CONFLICT (needs review)");
                
            } else {
                // Low confidence or needs_review
                result.needsReview.add(record);
                result.flaggedForReview++;
                System.out.println(String.format("⚠️  REVIEW (%.0f%% confidence)", 
                    validation.confidence * 100));
            }
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
        return result;
    }
    
    /**
     * Display import summary
     */
    public void displaySummary(ImportResult result) {
        System.out.println("📊 Import Analysis Summary");
        System.out.println("=".repeat(70));
        System.out.println();
        
        System.out.println("Total records processed: " + result.totalProcessed);
        System.out.println();
        
        // STEP 3: Safe records (auto-create)
        if (result.safeRecords.size() > 0) {
            System.out.println("✅ SAFE TO CREATE (" + result.safeRecords.size() + " records)");
            System.out.println("   These will be created automatically:");
            for (CSVRecord record : result.safeRecords) {
                System.out.println("   • " + record.hostname + " → " + record.ip);
            }
            System.out.println();
        }
        
        // STEP 4: Invalid/skipped records
        if (result.invalidRecords.size() > 0) {
            System.out.println("❌ INVALID/SKIPPED (" + result.invalidRecords.size() + " records)");
            System.out.println("   These will NOT be created:");
            for (CSVRecord record : result.invalidRecords) {
                System.out.println("   • " + record.hostname + " → " + record.ip);
            }
            System.out.println();
        }
        
        // STEP 6: Manual review needed
        if (result.needsReview.size() > 0) {
            System.out.println("⚠️  NEEDS REVIEW (" + result.needsReview.size() + " records)");
            System.out.println("   These require manual decision:");
            for (CSVRecord record : result.needsReview) {
                System.out.println("   • " + record.hostname + " → " + record.ip);
            }
            System.out.println();
        }
        
        // Summary stats
        System.out.println("-".repeat(70));
        System.out.println(String.format("Auto-create: %d | Skipped: %d | Review: %d",
            result.safeRecords.size(),
            result.invalidRecords.size(),
            result.needsReview.size()));
        System.out.println("=".repeat(70));
    }
}
