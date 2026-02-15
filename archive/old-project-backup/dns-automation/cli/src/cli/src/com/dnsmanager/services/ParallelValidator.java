package com.dnsmanager.services;

import com.dnsmanager.services.CSVParser.CSVRecord;
import com.dnsmanager.services.RuleBasedValidator.ValidationResult;
import java.util.*;
import java.util.concurrent.*;

/**
 * Parallel validation pipeline
 * Fast validation using concurrency
 */
public class ParallelValidator {
    
    private RuleBasedValidator ruleValidator;
    private NetworkValidator networkValidator;
    private AIValidator aiValidator;
    
    public ParallelValidator() {
        this.ruleValidator = new RuleBasedValidator();
        this.networkValidator = new NetworkValidator();
        this.aiValidator = new AIValidator();
    }
    
    public static class ValidationResultFull {
        public CSVRecord record;
        public ValidationResult ruleResult;
        public NetworkValidator.NetworkStatus networkStatus;
        public AIValidator.ValidationResult aiResult;
        
        public String getCategory() {
            // Phase 1: Rules check first (hard fail)
            if (!ruleResult.isValid) {
                return "invalid";
            }
            
            // Phase 2: Network check (critical warning)
            if (networkStatus != null && networkStatus.responds && !networkStatus.hasReverseDNS) {
                return "warning_active_ip";
            }
            
            // Phase 3: AI confidence (if available)
            if (aiResult != null && aiResult.confidence > 0.85) {
                return "safe";
            }
            
            // Default: needs review
            return "needs_review";
        }
        
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(record.hostname).append(" → ").append(record.ip);
            
            if (!ruleResult.isValid) {
                sb.append(" [INVALID: ").append(ruleResult.reason).append("]");
            } else if (networkStatus != null && !networkStatus.isSafe()) {
                sb.append(" [⚠️  ACTIVE IP]");
            } else if (aiResult != null) {
                sb.append(String.format(" [AI: %.0f%%]", aiResult.confidence * 100));
            }
            
            return sb.toString();
        }
    }
    
    /**
     * Validate records in parallel (FAST!)
     */
    public List<ValidationResultFull> validateRecords(List<CSVRecord> records, boolean useAI, boolean checkNetwork) {
        
        System.out.println("🚀 Parallel validation pipeline starting...");
        System.out.println("   Records: " + records.size());
        System.out.println("   AI: " + (useAI ? "enabled" : "disabled"));
        System.out.println("   Network check: " + (checkNetwork ? "enabled" : "disabled"));
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        
        List<ValidationResultFull> results = new ArrayList<>();
        
        // PHASE 1: Rule validation (fast, sequential is fine)
        System.out.println("Phase 1: Rules validation...");
        for (CSVRecord record : records) {
            ValidationResultFull result = new ValidationResultFull();
            result.record = record;
            result.ruleResult = ruleValidator.validateRecord(
                record.hostname, 
                record.ip, 
                record.type
            );
            results.add(result);
        }
        
        long phase1Time = System.currentTimeMillis() - startTime;
        System.out.println(String.format("✅ Rules validation done (%.1fs)", phase1Time / 1000.0));
        System.out.println();
        
        // PHASE 2: Network checks (parallel!)
        if (checkNetwork) {
            System.out.println("Phase 2: Network validation (parallel ping)...");
            long phase2Start = System.currentTimeMillis();
            
            // Collect IPs to check (only valid A records)
            List<String> ipsToCheck = new ArrayList<>();
            for (ValidationResultFull result : results) {
                if (result.ruleResult.isValid && result.record.type.equals("A")) {
                    ipsToCheck.add(result.record.ip);
                }
            }
            
            if (!ipsToCheck.isEmpty()) {
                System.out.println("   Checking " + ipsToCheck.size() + " IPs...");
                
                // Check all IPs in parallel
                Map<String, NetworkValidator.NetworkStatus> networkResults = 
                    networkValidator.checkIPs(ipsToCheck);
                
                // Assign results
                for (ValidationResultFull result : results) {
                    if (result.record.type.equals("A")) {
                        result.networkStatus = networkResults.get(result.record.ip);
                    }
                }
            }
            
            long phase2Time = System.currentTimeMillis() - phase2Start;
            System.out.println(String.format("✅ Network validation done (%.1fs)", phase2Time / 1000.0));
            System.out.println();
        }
        
        // PHASE 3: AI validation (parallel, optional)
        if (useAI && aiValidator.isAIAvailable()) {
            System.out.println("Phase 3: AI validation (parallel, advisory)...");
            long phase3Start = System.currentTimeMillis();
            
            ExecutorService executor = Executors.newFixedThreadPool(5);
            List<Future<AIValidator.ValidationResult>> futures = new ArrayList<>();
            List<ValidationResultFull> toValidate = new ArrayList<>();
            
            // Submit AI validation tasks (only for rule-valid records)
            for (ValidationResultFull result : results) {
                if (result.ruleResult.isValid) {
                    toValidate.add(result);
                    final CSVRecord rec = result.record;
                    futures.add(executor.submit(() -> 
                        aiValidator.validateRecord(rec.hostname, rec.ip, rec.zone)
                    ));
                }
            }
            
            System.out.println("   AI analyzing " + toValidate.size() + " records...");
            
            // Collect AI results
            for (int i = 0; i < futures.size(); i++) {
                try {
                    toValidate.get(i).aiResult = futures.get(i).get(5, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    System.err.println("⚠️  AI timeout for: " + toValidate.get(i).record.hostname);
                } catch (Exception e) {
                    // AI error - skip
                }
            }
            
            executor.shutdown();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            
            long phase3Time = System.currentTimeMillis() - phase3Start;
            System.out.println(String.format("✅ AI validation done (%.1fs)", phase3Time / 1000.0));
            System.out.println();
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("=".repeat(70));
        System.out.println(String.format("✅ Total validation time: %.1f seconds", totalTime / 1000.0));
        System.out.println("=".repeat(70));
        System.out.println();
        
        return results;
    }
}
