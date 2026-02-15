package com.dnsmanager.services;

import java.io.*;
import java.util.*;

/**
 * CSV Parser for DNS record imports
 * Handles reading and validating CSV files
 */
public class CSVParser {
    
    /**
     * Parsed DNS record from CSV
     */
    public static class CSVRecord {
        public String hostname;
        public String ip;
        public String type;
        public String zone;
        public int ttl = 86400;  // Default 24 hours
        public Integer priority;  // For MX/SRV
        public String comments;
        public int lineNumber;    // For error reporting
        
        public boolean isValid() {
            return hostname != null && !hostname.isEmpty() 
                && ip != null && !ip.isEmpty()
                && type != null && !type.isEmpty()
                && zone != null && !zone.isEmpty();
        }
        
        @Override
        public String toString() {
            return String.format("Line %d: %s → %s (%s) in %s", 
                lineNumber, hostname, ip, type, zone);
        }
    }
    
    /**
     * Parse result with records and errors
     */
    public static class ParseResult {
        public List<CSVRecord> records = new ArrayList<>();
        public List<String> errors = new ArrayList<>();
        public int totalLines = 0;
        public int validRecords = 0;
        public int invalidRecords = 0;
        
        public void addError(int lineNumber, String error) {
            errors.add("Line " + lineNumber + ": " + error);
            invalidRecords++;
        }
        
        public void addRecord(CSVRecord record) {
            records.add(record);
            validRecords++;
        }
    }
    
    /**
     * Parse CSV file
     */
    public ParseResult parseFile(String filepath) throws IOException {
        ParseResult result = new ParseResult();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            String line;
            int lineNumber = 0;
            String[] headers = null;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                result.totalLines++;
                
                line = line.trim();
                
                // Skip empty lines
                if (line.isEmpty()) {
                    continue;
                }
                
                // Parse header line
                if (lineNumber == 1) {
                    headers = parseLine(line);
                    validateHeaders(headers, result);
                    continue;
                }
                
                // Parse data line
                try {
                    CSVRecord record = parseLine(line, headers, lineNumber);
                    
                    if (record.isValid()) {
                        result.addRecord(record);
                    } else {
                        result.addError(lineNumber, "Missing required fields");
                    }
                    
                } catch (Exception e) {
                    result.addError(lineNumber, "Parse error: " + e.getMessage());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Parse CSV line into fields
     */
    private String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString().trim());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        
        fields.add(field.toString().trim());
        return fields.toArray(new String[0]);
    }
    
    /**
     * Parse data line into CSVRecord
     */
    private CSVRecord parseLine(String line, String[] headers, int lineNumber) {
        String[] fields = parseLine(line);
        CSVRecord record = new CSVRecord();
        record.lineNumber = lineNumber;
        
        for (int i = 0; i < headers.length && i < fields.length; i++) {
            String header = headers[i].toLowerCase();
            String value = fields[i];
            
            switch (header) {
                case "hostname":
                    record.hostname = value;
                    break;
                case "ip":
                case "value":
                    record.ip = value;
                    break;
                case "type":
                    record.type = value.toUpperCase();
                    break;
                case "zone":
                    record.zone = value;
                    break;
                case "ttl":
                    if (!value.isEmpty()) {
                        try {
                            record.ttl = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            record.ttl = 86400;
                        }
                    }
                    break;
                case "priority":
                    if (!value.isEmpty()) {
                        try {
                            record.priority = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // Ignore
                        }
                    }
                    break;
                case "comments":
                case "description":
                    record.comments = value;
                    break;
            }
        }
        
        return record;
    }
    
    /**
     * Validate CSV headers
     */
    private void validateHeaders(String[] headers, ParseResult result) {
        Set<String> required = new HashSet<>(Arrays.asList("hostname", "ip", "type", "zone"));
        Set<String> found = new HashSet<>();
        
        for (String header : headers) {
            found.add(header.toLowerCase());
        }
        
        for (String req : required) {
            if (!found.contains(req) && !found.contains("value")) {
                result.addError(1, "Missing required column: " + req);
            }
        }
    }
    
    /**
     * Create sample CSV file for testing
     */
    public static void createSample(String filepath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filepath))) {
            writer.println("hostname,ip,type,zone,ttl,priority,comments");
            writer.println("web-prod-01,10.10.10.50,A,prod.local,3600,,Production web server");
            writer.println("web-prod-02,10.10.10.51,A,prod.local,3600,,Production web server");
            writer.println("db-prod-01,10.10.10.60,A,prod.local,3600,,Database server");
            writer.println("cache-prod-01,10.10.10.70,A,prod.local,3600,,Cache server");
            writer.println("mail,10.10.10.80,MX,prod.local,3600,10,Mail server");
            writer.println("www,web-prod-01.prod.local,CNAME,prod.local,3600,,Web alias");
            writer.println("old-test-server,10.10.10.99,A,test.local,3600,,Old test server");
            writer.println("invalid-server,10.10.10.256,A,test.local,3600,,Invalid IP");
        }
    }
}
