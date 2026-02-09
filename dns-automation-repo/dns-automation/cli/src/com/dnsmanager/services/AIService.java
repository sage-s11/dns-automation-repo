package com.dnsmanager.services;

import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Service for communicating with Ollama AI
 * Handles all LLM interactions
 */
public class AIService {
    
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String MODEL = "llama3.2";
    private static final int TIMEOUT = 30000; // 30 seconds
    
    /**
     * Send prompt to AI and get response
     */
    public String prompt(String systemPrompt, String userPrompt) throws Exception {
        // Combine prompts
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;
        
        // Build request
        JSONObject request = new JSONObject();
        request.put("model", MODEL);
        request.put("prompt", fullPrompt);
        request.put("stream", false);
        request.put("temperature", 0.3); // Lower = more deterministic
        
        // Send to Ollama
        String response = sendRequest(request.toString());
        
        // Parse response
        JSONObject jsonResponse = new JSONObject(response);
        return jsonResponse.getString("response").trim();
    }
    
    /**
     * Get structured JSON response from AI
     */
    public JSONObject promptJSON(String systemPrompt, String userPrompt) throws Exception {
        String response = prompt(systemPrompt, userPrompt);
        
        // Extract JSON from response (AI might add extra text)
        String jsonStr = extractJSON(response);
        
        if (jsonStr == null) {
            throw new Exception("AI did not return valid JSON: " + response);
        }
        
        return new JSONObject(jsonStr);
    }
    
    /**
     * Send HTTP request to Ollama
     */
    private String sendRequest(String jsonInput) throws Exception {
        URL url = new URL(OLLAMA_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            // Configure connection
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            
            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            // Read response
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Ollama API error: HTTP " + responseCode);
            }
            
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                return response.toString();
            }
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Extract JSON from AI response
     * AI sometimes adds markdown or extra text
     */
    /**
     * Extract JSON from AI response (improved)
     */
    private String extractJSON(String response) {
        // Remove markdown code blocks
        response = response.replaceAll("```json\\s*", "");
        response = response.replaceAll("```\\s*", "");
        response = response.trim();
        
        // Find JSON object - look for balanced braces
        int start = -1;
        int end = -1;
        int braceCount = 0;
        
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            
            if (c == '{') {
                if (start == -1) start = i;
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0 && start != -1) {
                    end = i;
                    break;
                }
            }
        }
        
        if (start != -1 && end != -1) {
            String json = response.substring(start, end + 1);
            // Clean up any trailing quotes or commas that might be malformed
            return json.trim();
        }
        
        // Try array notation
        start = response.indexOf('[');
        end = response.lastIndexOf(']');
        
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return null;
    }
    
    /**
     * Test if Ollama is running
     */
    public boolean isAvailable() {
        try {
            URL url = new URL("http://localhost:11434/api/tags");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            
            int code = conn.getResponseCode();
            conn.disconnect();
            
            return code == 200;
            
        } catch (Exception e) {
            return false;
        }
    }
}
