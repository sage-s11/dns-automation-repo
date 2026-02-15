package com.dnsmanager;

import com.dnsmanager.services.AIService;
import org.json.JSONObject;

public class TestAI {
    public static void main(String[] args) {
        AIService ai = new AIService();
        
        System.out.println("🤖 Testing AI Service...\n");
        
        // Test 1: Check if Ollama is running
        if (!ai.isAvailable()) {
            System.out.println("❌ Ollama is not running!");
            System.out.println("Start it with: ollama serve");
            System.exit(1);
        }
        
        System.out.println("✅ Ollama is running\n");
        
        // Test 2: Simple prompt
        try {
            System.out.println("Test 1: Simple prompt");
            String response = ai.prompt(
                "You are a DNS expert.",
                "Say 'Hello DNS Engineer' in exactly 3 words"
            );
            System.out.println("AI: " + response);
            System.out.println();
            
            // Test 3: JSON response
            System.out.println("Test 2: JSON response");
            String systemPrompt = "You are a DNS validation expert. Respond ONLY in JSON format.";
            String userPrompt = """
                Is this a valid DNS record?
                Hostname: web-prod-01
                IP: 10.10.10.50
                
                Respond in JSON:
                {
                    "valid": true/false,
                    "reason": "explanation"
                }
                """;
            
            JSONObject json = ai.promptJSON(systemPrompt, userPrompt);
            System.out.println("Valid: " + json.getBoolean("valid"));
            System.out.println("Reason: " + json.getString("reason"));
            System.out.println();
            
            System.out.println("✅ All tests passed! AI is working!");
            
        } catch (Exception e) {
            System.out.println("❌ AI test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
