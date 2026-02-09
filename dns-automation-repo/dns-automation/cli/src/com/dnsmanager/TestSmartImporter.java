package com.dnsmanager;

import com.dnsmanager.services.SmartImporter;
import com.dnsmanager.services.SmartImporter.ImportResult;
import com.dnsmanager.services.CSVParser;

public class TestSmartImporter {
    public static void main(String[] args) {
        
        try {
            // Create test CSV
            String testFile = "/tmp/dns-smart-test.csv";
            System.out.println("Creating test CSV...\n");
            CSVParser.createSample(testFile);
            
            // Run smart importer
            SmartImporter importer = new SmartImporter();
            ImportResult result = importer.analyzeCSV(testFile);
            
            // Display summary
            System.out.println();
            importer.displaySummary(result);
            
        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
