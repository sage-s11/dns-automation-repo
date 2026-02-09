package com.dnsmanager;

import com.dnsmanager.services.CSVParser;
import com.dnsmanager.services.CSVParser.ParseResult;
import com.dnsmanager.services.CSVParser.CSVRecord;

import java.io.IOException;

public class TestCSVParser {
    public static void main(String[] args) {
        System.out.println("📊 Testing CSV Parser\n");
        
        CSVParser parser = new CSVParser();
        
        try {
            // Create sample CSV
            String testFile = "/tmp/dns-test.csv";
            System.out.println("Creating sample CSV: " + testFile);
            CSVParser.createSample(testFile);
            System.out.println("✅ Sample created\n");
            
            // Parse it
            System.out.println("Parsing CSV...");
            ParseResult result = parser.parseFile(testFile);
            
            // Show results
            System.out.println("=".repeat(60));
            System.out.println("📊 Parse Results:");
            System.out.println("=".repeat(60));
            System.out.println("Total lines: " + result.totalLines);
            System.out.println("Valid records: " + result.validRecords);
            System.out.println("Invalid records: " + result.invalidRecords);
            System.out.println();
            
            // Show valid records
            if (result.validRecords > 0) {
                System.out.println("✅ Valid Records:");
                System.out.println("-".repeat(60));
                for (CSVRecord record : result.records) {
                    System.out.println(record);
                }
                System.out.println();
            }
            
            // Show errors
            if (result.errors.size() > 0) {
                System.out.println("❌ Errors:");
                System.out.println("-".repeat(60));
                for (String error : result.errors) {
                    System.out.println("  " + error);
                }
            }
            
            System.out.println("\n✅ CSV parsing test complete!");
            
        } catch (IOException e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
