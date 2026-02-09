package com.dnsmanager.services;

import com.dnsmanager.models.DnsRecord;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * AI-powered DNS record validator
 * Implements intelligent validation using LLM
 */
public class AIValidator {
    
    private AIService ai;
    
    public AIValidator() {
        this.ai = new AIService();
    }
    
    /**
     * Validation result from AI
     */
    public static class ValidationResult {
        public boolean isValid;
        public String category;  // "safe", "conflict", "invalid", "needs_review"
        public double confidence;
        public String reason;
        public String recommendation;
        
        @Override
        public String toString() {
            return String.format("ValidationResult{valid=%s, category=%s, confidence=%.0f%%, reason=%s}",
                isValid, category, confidence * 100, reason);
        }
    }
    
    /**
     * Validate a single DNS record using AI
     */
    public ValidationResult validateRecord(String hostname, String ip, String zone) {
        
        ValidationResult result = new ValidationResult();
        
        try {
            // Build the prompt
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildValidationPrompt(hostname, ip, zone);
            
            // Get AI response
            JSONObject response = ai.promptJSON(systemPrompt, userPrompt);
            
            // Parse response
            result.isValid = response.getBoolean("valid");
            result.category = response.getString("category");
            result.confidence = response.getDouble("confidence");
            result.reason = response.getString("reason");
            
            if (response.has("recommendation")) {
                result.recommendation = response.getString("recommendation");
            }
            
        } catch (Exception e) {
            // Fallback to rule-based if AI fails
            System.err.println("⚠️  AI validation failed, using fallback: " + e.getMessage());
            result = fallbackValidation(hostname, ip);
        }
        
        return result;
    }
    
    /**
     * Validate naming pattern against existing records
     */
    public ValidationResult validateNamingPattern(String hostname, String zone, String[] existingHostnames) {
        
        ValidationResult result = new ValidationResult();
        
        try {
            String systemPrompt = "You are a DNS naming convention expert. Analyze if a hostname follows the pattern of existing hostnames.";
            
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("Existing hostnames in zone ").append(zone).append(":\n");
            
            for (String existing : existingHostnames) {
                userPrompt.append("- ").append(existing).append("\n");
            }
            
            userPrompt.append("\nNew hostname: ").append(hostname).append("\n\n");
            userPrompt.append("Does this follow the naming pattern? Respond in JSON:\n");
            userPrompt.append("{\n");
            userPrompt.append("  \"follows_pattern\": true/false,\n");
            userPrompt.append("  \"confidence\": 0.0-1.0,\n");
            userPrompt.append("  \"pattern_detected\": \"description of pattern\",\n");
            userPrompt.append("  \"reason\": \"explanation\"\n");
            userPrompt.append("}");
            
            JSONObject response = ai.promptJSON(systemPrompt, userPrompt.toString());
            
            result.isValid = response.getBoolean("follows_pattern");
            result.confidence = response.getDouble("confidence");
            result.reason = response.getString("reason");
            result.category = result.isValid ? "safe" : "needs_review";
            
            if (response.has("pattern_detected")) {
                result.recommendation = "Pattern: " + response.getString("pattern_detected");
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Pattern validation failed: " + e.getMessage());
            result.isValid = true; // Default to allowing if AI fails
            result.confidence = 0.5;
            result.category = "needs_review";
            result.reason = "AI analysis unavailable";
        }
        
        return result;
    }
    
    /**
     * Analyze if old record should be removed (stale detection)
     */
    public ValidationResult analyzeStaleRecord(String hostname, String ip, int daysOld, int daysSinceLastQuery) {
        
        ValidationResult result = new ValidationResult();
        
        try {
            String systemPrompt = "You are a DNS lifecycle expert. Determine if old DNS records should be removed based on age and activity.";
            
            String userPrompt = String.format("""
                DNS Record Analysis:
                - Hostname: %s
                - IP: %s
                - Age: %d days
                - Last DNS query: %d days ago
                
                Should this record be removed? Consider:
                - Records inactive >180 days are often stale
                - Production servers typically queried regularly
                - Test servers may be inactive but still needed
                
                Respond in JSON:
                {
                    "should_remove": true/false,
                    "confidence": 0.0-1.0,
                    "reason": "explanation",
                    "risk_level": "low/medium/high"
                }
                """, hostname, ip, daysOld, daysSinceLastQuery);
            
            JSONObject response = ai.promptJSON(systemPrompt, userPrompt);
            
            boolean shouldRemove = response.getBoolean("should_remove");
            result.isValid = !shouldRemove; // If should remove, then not valid to keep
            result.confidence = response.getDouble("confidence");
            result.reason = response.getString("reason");
            
            String riskLevel = response.getString("risk_level");
            if (shouldRemove) {
                result.category = riskLevel.equals("low") ? "safe_to_remove" : "recommend_remove";
                result.recommendation = "Consider removing this stale record";
            } else {
                result.category = "keep";
                result.recommendation = "Keep this record";
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Stale analysis failed: " + e.getMessage());
            result.isValid = true; // Default to keeping record if uncertain
            result.category = "needs_review";
            result.confidence = 0.0;
        }
        
        return result;
    }
    
    /**
     * Build system prompt for DNS validation
     */
    private String buildSystemPrompt() {
        return """
            You are an expert DNS administrator with deep knowledge of DNS standards, 
            naming conventions, and best practices.
            
            Your role is to validate DNS records for correctness and safety.
            
            DNS Rules:
            - Hostnames: 1-63 characters, letters/numbers/hyphens, no leading/trailing hyphen
            - IPv4: Four octets 0-255, dotted notation (e.g., 10.10.10.50)
            - Valid characters: a-z, A-Z, 0-9, hyphen (-)
            - Reserved: localhost, any names starting with numbers
            
            Always respond in valid JSON format only.
            """;
    }
    
    /**
     * Build validation prompt for a record
     */
    private String buildValidationPrompt(String hostname, String ip, String zone) {
        return String.format("""
            Validate this DNS record:
            - Hostname: %s
            - IP Address: %s
            - Zone: %s
            
            Check:
            1. Hostname format (valid characters, length)
            2. IP address format (valid IPv4)
            3. No obvious conflicts or issues
            
            Respond in JSON:
            {
                "valid": true/false,
                "category": "safe/invalid/needs_review",
                "confidence": 0.0-1.0,
                "reason": "brief explanation"
            }
            """, hostname, ip, zone);
    }
    
    /**
     * Fallback rule-based validation if AI fails
     */
    private ValidationResult fallbackValidation(String hostname, String ip) {
        ValidationResult result = new ValidationResult();
        
        // Basic IP validation
        if (!ip.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            result.isValid = false;
            result.category = "invalid";
            result.confidence = 1.0;
            result.reason = "Invalid IP format";
            return result;
        }
        
        // Basic hostname validation
        if (!hostname.matches("[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?")) {
            result.isValid = false;
            result.category = "invalid";
            result.confidence = 1.0;
            result.reason = "Invalid hostname format";
            return result;
        }
        
        // Default: valid but needs review
        result.isValid = true;
        result.category = "needs_review";
        result.confidence = 0.6;
        result.reason = "Passed basic validation (AI unavailable)";
        
        return result;
    }
    
    /**
     * Check if AI service is available
     */
    public boolean isAIAvailable() {
        return ai.isAvailable();
    }
}
