package com.dnsmanager.services;

/**
 * Deterministic DNS validation following RFC 952/1123
 * No AI - pure rules-based validation
 * 
 * Now supports: A, CNAME, MX, TXT records
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
        
        // Remove CIDR notation if present
        String cleanIP = ip.split("/")[0];
        
        if (!cleanIP.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
            return ValidationResult.invalid("Invalid IP format");
        }
        
        String[] octets = cleanIP.split("\\.");
        
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
    
    /**
     * Validate record - backward compatible (no priority)
     */
    public ValidationResult validateRecord(String hostname, String value, String type) {
        return validateRecord(hostname, value, type, null);
    }
    
    /**
     * Validate record with priority support
     */
    public ValidationResult validateRecord(String hostname, String value, String type, Integer priority) {
        // Allow @ for zone apex
        if (!hostname.equals("@") && !hostname.equals("*")) {
            ValidationResult hostnameResult = validateHostname(hostname);
            if (!hostnameResult.isValid) {
                return hostnameResult;
            }
        }
        
        type = type.toUpperCase();
        
        switch (type) {
            case "A":
                return validateIPv4(value);
            
            case "CNAME":
                return validateCNAME(hostname, value);
            
            case "MX":
                return validateMX(hostname, value, priority);
            
            case "TXT":
                return validateTXT(hostname, value);
            
            default:
                return ValidationResult.valid();
        }
    }
    
    /**
     * Validate CNAME record
     */
    private ValidationResult validateCNAME(String hostname, String target) {
        // Validate target hostname
        if (!target.equals("@")) {
            ValidationResult targetResult = validateHostname(target);
            if (!targetResult.isValid) {
                return ValidationResult.invalid("Invalid CNAME target: " + targetResult.reason);
            }
        }
        
        // Check for self-reference
        if (hostname.equalsIgnoreCase(target)) {
            return ValidationResult.invalid("CNAME cannot point to itself");
        }
        
        // Zone apex CNAME is not recommended but we'll allow it
        if (hostname.equals("@")) {
            // Some DNS servers support this, so just validate the target
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate MX record
     */
    private ValidationResult validateMX(String hostname, String mailServer, Integer priority) {
        // Priority is required for MX
        if (priority == null) {
            return ValidationResult.invalid("MX records require a priority value");
        }
        
        // Validate priority range (RFC 5321)
        if (priority < 0 || priority > 65535) {
            return ValidationResult.invalid("MX priority must be between 0 and 65535");
        }
        
        // Validate mail server target
        if (!mailServer.equals("@")) {
            ValidationResult targetResult = validateHostname(mailServer);
            if (!targetResult.isValid) {
                return ValidationResult.invalid("Invalid mail server hostname: " + targetResult.reason);
            }
        }
        
        return ValidationResult.valid();
    }
    
    /**
     * Validate TXT record
     */
    private ValidationResult validateTXT(String hostname, String text) {
        // TXT value is required
        if (text == null) {
            return ValidationResult.invalid("TXT value cannot be null");
        }
        
        // Check length (DNS TXT record limit)
        if (text.length() > 65535) {
            return ValidationResult.invalid("TXT record exceeds maximum length (65535 characters)");
        }
        
        // TXT records can contain any text, including empty strings
        return ValidationResult.valid();
    }
}
