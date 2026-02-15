package com.dnsmanager.services;

/**
 * Deterministic DNS validation following RFC 952/1123
 * No AI - pure rules-based validation
 */
public class RuleBasedValidator {
    
    public static class ValidationResult {
        public boolean isValid;
        public String category;
        public String reason;
        
        public static ValidationResult valid() {
            ValidationResult r = new ValidationResult();
            r.isValid = true;
            r.category = "valid";
            r.reason = "Passed validation";
            return r;
        }
        
        public static ValidationResult invalid(String reason) {
            ValidationResult r = new ValidationResult();
            r.isValid = false;
            r.category = "invalid";
            r.reason = reason;
            return r;
        }
    }
    
    public ValidationResult validateHostname(String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return ValidationResult.invalid("Hostname cannot be empty");
        }
        
        hostname = hostname.toLowerCase();
        
        if (hostname.length() > 253) {
            return ValidationResult.invalid("Hostname too long (max 253 characters)");
        }
        
        String[] labels = hostname.split("\\.");
        
        for (String label : labels) {
            if (label.length() < 1) {
                return ValidationResult.invalid("Empty label in hostname");
            }
            
            if (label.length() > 63) {
                return ValidationResult.invalid("Label too long (max 63 characters)");
            }
            
            if (label.startsWith("-")) {
                return ValidationResult.invalid("Label cannot start with hyphen");
            }
            
            if (label.endsWith("-")) {
                return ValidationResult.invalid("Label cannot end with hyphen");
            }
            
            if (!label.matches("^[a-z0-9]([a-z0-9\\-]*[a-z0-9])?$")) {
                return ValidationResult.invalid("Label contains invalid characters");
            }
        }
        
        return ValidationResult.valid();
    }
    
    public ValidationResult validateIPv4(String ip) {
        if (ip == null || ip.isEmpty()) {
            return ValidationResult.invalid("IP address cannot be empty");
        }
        
        if (!ip.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return ValidationResult.invalid("Invalid IP format");
        }
        
        String[] octets = ip.split("\\.");
        
        if (octets.length != 4) {
            return ValidationResult.invalid("IPv4 must have 4 octets");
        }
        
        for (int i = 0; i < 4; i++) {
            try {
                int value = Integer.parseInt(octets[i]);
                if (value < 0 || value > 255) {
                    return ValidationResult.invalid("Octet out of range (0-255)");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.invalid("Invalid octet");
            }
        }
        
        return ValidationResult.valid();
    }
    
    public ValidationResult validateRecord(String hostname, String value, String type) {
        ValidationResult hostnameResult = validateHostname(hostname);
        if (!hostnameResult.isValid) {
            return hostnameResult;
        }
        
        type = type.toUpperCase();
        
        if (type.equals("A")) {
            return validateIPv4(value);
        }
        
        return ValidationResult.valid();
    }
}
