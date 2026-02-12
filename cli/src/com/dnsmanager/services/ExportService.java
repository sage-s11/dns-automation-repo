package com.dnsmanager.services;

import com.dnsmanager.services.EnterpriseNetworkValidator.IPValidationResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Export Service - Smart DNS export with network analysis
 * 
 * Features:
 * - Network validation (detect active vs inactive IPs)
 * - Stale record detection (old records, no response)
 * - Risk assessment
 * - Categorized output (active/inactive/stale)
 * - Actionable recommendations
 */
public class ExportService extends DatabaseService {
    
    private EnterpriseNetworkValidator networkValidator;
    
    public ExportService() {
        this.networkValidator = new EnterpriseNetworkValidator();
    }
    
    /**
     * Export zone with analysis
     */
    public ExportResult exportZone(String zoneName, String outputFile, 
                                   boolean splitFiles) throws Exception {
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, dr.ttl, " +
                     "       dr.priority, dr.created_at, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE z.name = ? " +
                     "ORDER BY dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, zoneName);
            return executeExport(stmt, outputFile, splitFiles);
        }
    }
    
    /**
     * Export by pattern
     */
    public ExportResult exportByPattern(String pattern, String outputFile,
                                       boolean splitFiles) throws Exception {
        String sqlPattern = pattern.replace("*", "%");
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, dr.ttl, " +
                     "       dr.priority, dr.created_at, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "WHERE dr.hostname LIKE ? " +
                     "ORDER BY dr.hostname";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, sqlPattern);
            return executeExport(stmt, outputFile, splitFiles);
        }
    }
    
    /**
     * Export all records
     */
    public ExportResult exportAll(String outputFile, boolean splitFiles) throws Exception {
        String sql = "SELECT dr.id, dr.hostname, dr.type, dr.value, dr.ttl, " +
                     "       dr.priority, dr.created_at, z.name as zone " +
                     "FROM dns_records dr " +
                     "JOIN zones z ON dr.zone_id = z.id " +
                     "ORDER BY z.name, dr.hostname";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            return executeExport(stmt.executeQuery(sql), outputFile, splitFiles);
        }
    }
    
    /**
     * Execute export with analysis
     */
    private ExportResult executeExport(PreparedStatement stmt, String outputFile,
                                      boolean splitFiles) throws Exception {
        try (ResultSet rs = stmt.executeQuery()) {
            return executeExport(rs, outputFile, splitFiles);
        }
    }
    
    /**
     * Core export logic
     */
    private ExportResult executeExport(ResultSet rs, String outputFile,
                                      boolean splitFiles) throws Exception {
        long startTime = System.currentTimeMillis();
        
        ExportResult result = new ExportResult();
        result.outputFile = outputFile;
        result.splitFiles = splitFiles;
        
        // Read records from database
        List<ExportRecord> records = new ArrayList<>();
        while (rs.next()) {
            ExportRecord record = new ExportRecord();
            record.hostname = rs.getString("hostname");
            record.ip = rs.getString("value");
            record.type = rs.getString("type");
            record.zone = rs.getString("zone");
            record.ttl = rs.getInt("ttl");
            record.priority = rs.getObject("priority") != null ? rs.getInt("priority") : null;
            record.createdAt = rs.getTimestamp("created_at");
            
            records.add(record);
        }
        
        result.totalRecords = records.size();
        
        if (records.isEmpty()) {
            System.out.println("ℹ️  No records found to export");
            return result;
        }
        
        System.out.println("📋 Found " + records.size() + " record(s)");
        
        // Always analyze records (smart export)
        System.out.println("\n🔍 Analyzing network status... (this may take a few seconds)");
        long analysisStart = System.currentTimeMillis();
        
        analyzeRecords(records, result);
        
        result.analysisTimeSeconds = (System.currentTimeMillis() - analysisStart) / 1000.0;
        
        // Write export files
        long exportStart = System.currentTimeMillis();
        
        if (splitFiles) {
            writeSplitFiles(records, result);
        } else {
            writeSingleFile(records, result);
        }
        
        result.exportTimeSeconds = (System.currentTimeMillis() - exportStart) / 1000.0;
        result.totalTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        
        return result;
    }
    
    /**
     * Analyze records for status and recommendations
     */
    private void analyzeRecords(List<ExportRecord> records, ExportResult result) {
        // Extract unique IPs for validation
        Set<String> uniqueIPs = new HashSet<>();
        for (ExportRecord record : records) {
            if (record.type.equals("A") && isValidIP(record.ip)) {
                uniqueIPs.add(record.ip);
            }
        }
        
        if (uniqueIPs.isEmpty()) {
            System.out.println("ℹ️  No A records with IPs to validate");
            for (ExportRecord record : records) {
                record.status = "N/A";
                record.notes = "Non-A record or invalid IP";
                result.inactiveCount++;
            }
            return;
        }
        
        // Validate IPs
        Map<String, IPValidationResult> validationResults = 
            networkValidator.validateIPs(new ArrayList<>(uniqueIPs));
        
        // Analyze each record
        LocalDateTime oneYearAgo = LocalDateTime.now().minus(1, ChronoUnit.YEARS);
        
        for (ExportRecord record : records) {
            if (!record.type.equals("A") || !isValidIP(record.ip)) {
                record.status = "N/A";
                record.riskLevel = "LOW";
                record.notes = "Non-A record";
                result.inactiveCount++;
                continue;
            }
            
            IPValidationResult validation = validationResults.get(record.ip);
            
            if (validation != null && validation.isActive) {
                // Active device detected
                record.status = "ACTIVE";
                record.riskLevel = "HIGH";
                record.notes = "Device responding - " + validation.detectionMethod;
                if (!validation.openPorts.isEmpty()) {
                    record.notes += " (ports: " + validation.openPorts + ")";
                }
                
                result.activeCount++;
                result.activeRecords.add(formatRecordSummary(record));
                
            } else {
                // No response
                record.status = "INACTIVE";
                record.riskLevel = "SAFE";
                record.notes = "No response";
                
                // Check if stale (old + inactive)
                if (record.createdAt != null) {
                    LocalDateTime createdDate = record.createdAt.toLocalDateTime();
                    if (createdDate.isBefore(oneYearAgo)) {
                        record.status = "STALE";
                        record.riskLevel = "MEDIUM";
                        record.notes = "Created " + formatAge(createdDate) + " ago, no response - cleanup candidate";
                        
                        result.staleCount++;
                        result.staleRecords.add(formatRecordSummary(record));
                    }
                }
                
                result.inactiveCount++;
            }
        }
    }
    
    /**
     * Write single export file
     */
    private void writeSingleFile(List<ExportRecord> records, ExportResult result) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(result.outputFile))) {
            // Write header - always include status columns
            writer.write("hostname,ip,type,zone,ttl,status,risk_level,notes");
            if (records.stream().anyMatch(r -> r.priority != null)) {
                writer.write(",priority");
            }
            writer.newLine();
            
            // Write records
            for (ExportRecord record : records) {
                writer.write(escapeCsv(record.hostname) + ",");
                writer.write(escapeCsv(record.ip) + ",");
                writer.write(record.type + ",");
                writer.write(escapeCsv(record.zone) + ",");
                writer.write(String.valueOf(record.ttl) + ",");
                writer.write(record.status + ",");
                writer.write(record.riskLevel + ",");
                writer.write(escapeCsv(record.notes));
                
                if (record.priority != null) {
                    writer.write("," + record.priority);
                }
                
                writer.newLine();
            }
        }
        
        System.out.println("✅ Exported to: " + result.outputFile);
    }
    
    /**
     * Write split files by category
     */
    private void writeSplitFiles(List<ExportRecord> records, ExportResult result) throws Exception {
        String baseName = result.outputFile.replace(".csv", "");
        
        result.activeFile = baseName + "-active.csv";
        result.inactiveFile = baseName + "-inactive.csv";
        result.staleFile = baseName + "-stale.csv";
        result.reportFile = baseName + "-REPORT.txt";
        
        // Categorize records
        List<ExportRecord> activeRecords = new ArrayList<>();
        List<ExportRecord> inactiveRecords = new ArrayList<>();
        List<ExportRecord> staleRecords = new ArrayList<>();
        
        for (ExportRecord record : records) {
            if ("ACTIVE".equals(record.status)) {
                activeRecords.add(record);
            } else if ("STALE".equals(record.status)) {
                staleRecords.add(record);
            } else {
                inactiveRecords.add(record);
            }
        }
        
        // Write main file (all records)
        writeCategoryFile(result.outputFile, records, "All Records");
        
        // Write categorized files
        if (!activeRecords.isEmpty()) {
            writeCategoryFile(result.activeFile, activeRecords, "Active Records");
        }
        if (!inactiveRecords.isEmpty()) {
            writeCategoryFile(result.inactiveFile, inactiveRecords, "Inactive Records");
        }
        if (!staleRecords.isEmpty()) {
            writeCategoryFile(result.staleFile, staleRecords, "Stale Records");
        }
        
        // Write analysis report
        writeAnalysisReport(result, records);
        
        System.out.println("✅ Created " + (3 + (staleRecords.isEmpty() ? 0 : 1)) + " categorized files");
    }
    
    /**
     * Write category file
     */
    private void writeCategoryFile(String filename, List<ExportRecord> records, String category) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("hostname,ip,type,zone,ttl,status,risk_level,notes");
            writer.newLine();
            
            for (ExportRecord record : records) {
                writer.write(escapeCsv(record.hostname) + ",");
                writer.write(escapeCsv(record.ip) + ",");
                writer.write(record.type + ",");
                writer.write(escapeCsv(record.zone) + ",");
                writer.write(String.valueOf(record.ttl) + ",");
                writer.write(record.status + ",");
                writer.write(record.riskLevel + ",");
                writer.write(escapeCsv(record.notes));
                writer.newLine();
            }
        }
    }
    
    /**
     * Write analysis report
     */
    private void writeAnalysisReport(ExportResult result, List<ExportRecord> records) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(result.reportFile))) {
            writer.write("======================================================================\n");
            writer.write("DNS EXPORT ANALYSIS REPORT\n");
            writer.write("======================================================================\n");
            writer.write("Generated: " + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            writer.write("Total records: " + result.totalRecords + "\n\n");
            
            writer.write("SUMMARY:\n");
            writer.write("  Active records:   " + result.activeCount + " (devices responding)\n");
            writer.write("  Inactive records: " + result.inactiveCount + " (no response)\n");
            writer.write("  Stale records:    " + result.staleCount + " (>1 year old, inactive)\n\n");
            
            if (result.activeCount > 0) {
                writer.write("ACTIVE DEVICES (DO NOT DELETE):\n");
                writer.write("----------------------------------------------------------------------\n");
                for (String record : result.activeRecords) {
                    writer.write("  " + record + "\n");
                }
                writer.write("\n");
            }
            
            if (result.staleCount > 0) {
                writer.write("STALE RECORDS (CLEANUP CANDIDATES):\n");
                writer.write("----------------------------------------------------------------------\n");
                for (String record : result.staleRecords) {
                    writer.write("  " + record + "\n");
                }
                writer.write("\n");
            }
            
            writer.write("RECOMMENDATIONS:\n");
            writer.write("----------------------------------------------------------------------\n");
            if (result.staleCount > 0) {
                writer.write("  - Review " + result.staleCount + " stale record(s) for cleanup\n");
            }
            if (result.inactiveCount > 10) {
                writer.write("  - " + result.inactiveCount + " inactive records safe to cleanup\n");
                writer.write("    (if not seasonal/backup devices)\n");
            }
            if (result.activeCount > 0) {
                writer.write("  - " + result.activeCount + " active device(s) detected\n");
                writer.write("    DO NOT DELETE without review\n");
            }
            writer.write("\n");
            
            writer.write("FILES CREATED:\n");
            writer.write("  Main export:    " + result.outputFile + "\n");
            writer.write("  Active:         " + result.activeFile + "\n");
            writer.write("  Inactive:       " + result.inactiveFile + "\n");
            if (result.staleCount > 0) {
                writer.write("  Stale:          " + result.staleFile + "\n");
            }
            writer.write("  This report:    " + result.reportFile + "\n");
            writer.write("======================================================================\n");
        }
    }
    
    /**
     * Format record for summary display
     */
    private String formatRecordSummary(ExportRecord record) {
        return record.hostname + " → " + record.ip + " [" + record.notes + "]";
    }
    
    /**
     * Format age from date
     */
    private String formatAge(LocalDateTime date) {
        long years = ChronoUnit.YEARS.between(date, LocalDateTime.now());
        if (years > 0) {
            return years + " year" + (years > 1 ? "s" : "");
        }
        long months = ChronoUnit.MONTHS.between(date, LocalDateTime.now());
        return months + " month" + (months > 1 ? "s" : "");
    }
    
    /**
     * Validate IP address format
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        // Remove CIDR notation if present
        String cleanIP = ip.split("/")[0];
        
        String[] parts = cleanIP.split("\\.");
        if (parts.length != 4) return false;
        
        try {
            for (String part : parts) {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255) return false;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Escape CSV value
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Export record with analysis
     */
    private static class ExportRecord {
        String hostname;
        String ip;
        String type;
        String zone;
        int ttl;
        Integer priority;
        Timestamp createdAt;
        String status = "UNKNOWN";
        String riskLevel = "UNKNOWN";
        String notes = "";
    }
    
    /**
     * Export result with statistics
     */
    public static class ExportResult {
        public String outputFile;
        public boolean splitFiles;
        
        public int totalRecords;
        public int activeCount;
        public int inactiveCount;
        public int staleCount;
        
        public List<String> activeRecords = new ArrayList<>();
        public List<String> staleRecords = new ArrayList<>();
        
        public String activeFile;
        public String inactiveFile;
        public String staleFile;
        public String reportFile;
        
        public double totalTimeSeconds;
        public double analysisTimeSeconds;
        public double exportTimeSeconds;
    }
}
