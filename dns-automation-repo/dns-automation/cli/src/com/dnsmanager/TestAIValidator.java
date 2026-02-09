package com.dnsmanager;

import com.dnsmanager.services.AIValidator;
import com.dnsmanager.services.AIValidator.ValidationResult;

public class TestAIValidator {
    public static void main(String[] args) {
        
        System.out.println("🤖 Testing AI DNS Validator\n");
        System.out.println("=".repeat(60));
        
        AIValidator validator = new AIValidator();
        
        // Check if AI is available
        if (!validator.isAIAvailable()) {
            System.out.println("❌ Ollama not running! Start with: ollama serve");
            System.exit(1);
        }
        
        System.out.println("✅ AI is ready\n");
        
        // Test 1: Valid record
        System.out.println("Test 1: Valid DNS Record");
        System.out.println("-".repeat(60));
        ValidationResult result1 = validator.validateRecord("web-prod-01", "10.10.10.50", "prod.local");
        printResult(result1);
        
        // Test 2: Invalid IP
        System.out.println("\nTest 2: Invalid IP Address");
        System.out.println("-".repeat(60));
        ValidationResult result2 = validator.validateRecord("web-server", "10.10.10.256", "prod.local");
        printResult(result2);
        
        // Test 3: Invalid hostname
        System.out.println("\nTest 3: Invalid Hostname");
        System.out.println("-".repeat(60));
        ValidationResult result3 = validator.validateRecord("-invalid-name", "10.10.10.60", "prod.local");
        printResult(result3);
        
        // Test 4: Naming pattern validation
        System.out.println("\nTest 4: Naming Pattern Analysis");
        System.out.println("-".repeat(60));
        String[] existing = {
            "web-prod-01",
            "web-prod-02",
            "db-prod-01",
            "api-prod-01"
        };
        ValidationResult result4 = validator.validateNamingPattern("cache-prod-01", "prod.local", existing);
        System.out.println("New hostname: cache-prod-01");
        System.out.println("Pattern match: " + result4.isValid);
        printResult(result4);
        
        // Test 5: Bad pattern
        System.out.println("\nTest 5: Naming Pattern Mismatch");
        System.out.println("-".repeat(60));
        ValidationResult result5 = validator.validateNamingPattern("my-random-server", "prod.local", existing);
        System.out.println("New hostname: my-random-server");
        System.out.println("Pattern match: " + result5.isValid);
        printResult(result5);
        
        // Test 6: Stale record detection
        System.out.println("\nTest 6: Stale Record Detection");
        System.out.println("-".repeat(60));
        ValidationResult result6 = validator.analyzeStaleRecord(
            "old-test-server", 
            "10.10.10.99", 
            547,  // 547 days old
            547   // Last query 547 days ago
        );
        System.out.println("Record: old-test-server (547 days old, no queries)");
        printResult(result6);
        
        // Test 7: Active record (should keep)
        System.out.println("\nTest 7: Active Record (Recent Activity)");
        System.out.println("-".repeat(60));
        ValidationResult result7 = validator.analyzeStaleRecord(
            "active-prod-web",
            "10.10.10.100",
            365,  // 1 year old
            2     // Queried 2 days ago
        );
        System.out.println("Record: active-prod-web (queried 2 days ago)");
        printResult(result7);
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ All AI validation tests complete!");
    }
    
    private static void printResult(ValidationResult result) {
        System.out.println("  Valid: " + (result.isValid ? "✅" : "❌"));
        System.out.println("  Category: " + result.category);
        System.out.println("  Confidence: " + String.format("%.0f%%", result.confidence * 100));
        System.out.println("  Reason: " + result.reason);
        if (result.recommendation != null) {
            System.out.println("  Recommendation: " + result.recommendation);
        }
    }
}

