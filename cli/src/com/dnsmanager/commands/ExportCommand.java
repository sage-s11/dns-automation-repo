package com.dnsmanager.commands;

import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.services.ExportService;
import com.dnsmanager.services.ExportService.ExportResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * DNS Export Command - Smart Export with Network Analysis
 * 
 * Exports DNS records with intelligent analysis:
 * - Network status (active/inactive)
 * - Stale record detection
 * - Risk assessment
 * - Actionable recommendations
 * 
 * Usage:
 *   Export zone:        --zone prod.local
 *   Export pattern:     --pattern "web-*"
 *   Export all:         --all
 *   Quick mode:         --quick (no validation)
 *   Split files:        --split (categorized output)
 */
public class ExportCommand {
    
    private static ExportService exportService;
    
    public static void main(String[] args) {
        System.out.println("📤 DNS Export Tool");
        System.out.println("======================================================================");
        
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }
        
        try {
            // Parse arguments
            String zone = null;
            String pattern = null;
            String outputFile = null;
            boolean exportAll = false;
            boolean splitFiles = false;
            
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--zone":
                        zone = args[++i];
                        break;
                    case "--pattern":
                        pattern = args[++i];
                        break;
                    case "--output":
                        outputFile = args[++i];
                        break;
                    case "--all":
                        exportAll = true;
                        break;
                    case "--split":
                        splitFiles = true;
                        break;
                    case "--help":
                        printUsage();
                        System.exit(0);
                        break;
                }
            }
            
            // Validate arguments
            if (zone == null && pattern == null && !exportAll) {
                System.err.println("❌ Must specify --zone, --pattern, or --all");
                printUsage();
                System.exit(1);
            }
            
            // Generate default output filename if not specified
            if (outputFile == null) {
                String timestamp = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
                );
                if (zone != null) {
                    outputFile = zone.replace(".", "-") + "-export-" + timestamp + ".csv";
                } else if (pattern != null) {
                    outputFile = "pattern-export-" + timestamp + ".csv";
                } else {
                    outputFile = "full-export-" + timestamp + ".csv";
                }
            }
            
            // Initialize services
            DatabaseConfig.initialize();
            exportService = new ExportService();
            
            // Display mode
            System.out.println("Mode: SMART EXPORT (with network analysis)");
            if (splitFiles) {
                System.out.println("Output: SPLIT (categorized files)");
            }
            System.out.println();
            
            // Execute export
            ExportResult result;
            if (zone != null) {
                result = exportByZone(zone, outputFile, splitFiles);
            } else if (pattern != null) {
                result = exportByPattern(pattern, outputFile, splitFiles);
            } else {
                result = exportAll(outputFile, splitFiles);
            }
            
            // Print summary
            printSummary(result);
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            try {
                DatabaseConfig.close();
                System.out.println("\n✅ Database connection closed");
            } catch (Exception e) {
                System.err.println("⚠️  Error closing database: " + e.getMessage());
            }
        }
    }
    
    /**
     * Export by zone
     */
    private static ExportResult exportByZone(String zoneName, String outputFile, 
                                             boolean splitFiles) 
            throws Exception {
        System.out.println("🔍 Exporting zone: " + zoneName);
        return exportService.exportZone(zoneName, outputFile, splitFiles);
    }
    
    /**
     * Export by pattern
     */
    private static ExportResult exportByPattern(String pattern, String outputFile,
                                                boolean splitFiles) 
            throws Exception {
        System.out.println("🔍 Exporting records matching pattern: " + pattern);
        return exportService.exportByPattern(pattern, outputFile, splitFiles);
    }
    
    /**
     * Export all records
     */
    private static ExportResult exportAll(String outputFile, boolean splitFiles) 
            throws Exception {
        System.out.println("🔍 Exporting all DNS records");
        return exportService.exportAll(outputFile, splitFiles);
    }
    
    /**
     * Print export summary with analysis
     */
    private static void printSummary(ExportResult result) {
        System.out.println("\n======================================================================");
        System.out.println("📊 EXPORT SUMMARY");
        System.out.println("======================================================================");
        System.out.println("Total records exported: " + result.totalRecords);
        
        if (result.totalRecords > 0) {
            System.out.println();
            
            // Active records
            if (result.activeCount > 0) {
                System.out.println("✅ ACTIVE RECORDS (" + result.activeCount + "):");
                System.out.println("   Devices currently responding on network");
                if (result.activeRecords.size() <= 10) {
                    for (String record : result.activeRecords) {
                        System.out.println("   - " + record);
                    }
                } else {
                    for (int i = 0; i < 5; i++) {
                        System.out.println("   - " + result.activeRecords.get(i));
                    }
                    System.out.println("   ... and " + (result.activeRecords.size() - 5) + " more");
                }
            }
            
            System.out.println();
            
            // Inactive records
            System.out.println("⚪ INACTIVE RECORDS (" + result.inactiveCount + "):");
            System.out.println("   No network response - safe to modify/delete");
            
            System.out.println();
            
            // Stale records
            if (result.staleCount > 0) {
                System.out.println("🧹 STALE RECORDS (" + result.staleCount + "):");
                System.out.println("   Created >1 year ago, no response - cleanup candidates");
                for (String record : result.staleRecords) {
                    System.out.println("   - " + record);
                }
                System.out.println();
            }
            
            // Recommendations
            System.out.println("💡 RECOMMENDATIONS:");
            if (result.staleCount > 0) {
                System.out.println("   - Review " + result.staleCount + " stale record(s) for cleanup");
            }
            if (result.inactiveCount > 10) {
                System.out.println("   - " + result.inactiveCount + 
                    " inactive records safe to cleanup if not seasonal/backup");
            }
            if (result.activeCount > 0) {
                System.out.println("   - " + result.activeCount + 
                    " active device(s) - do not delete without review");
            }
        }
        
        System.out.println();
        System.out.println("📁 Output files:");
        if (result.splitFiles) {
            System.out.println("   Main export:    " + result.outputFile);
            System.out.println("   Active records: " + result.activeFile);
            System.out.println("   Inactive:       " + result.inactiveFile);
            if (result.staleCount > 0) {
                System.out.println("   Stale records:  " + result.staleFile);
            }
            System.out.println("   Report:         " + result.reportFile);
        } else {
            System.out.println("   " + result.outputFile);
        }
        
        System.out.println("======================================================================");
        
        System.out.println("\n⏱️  Total time: " + result.totalTimeSeconds + "s");
        System.out.println("   Analysis: " + result.analysisTimeSeconds + "s");
        System.out.println("   Export: " + result.exportTimeSeconds + "s");
    }
    
    /**
     * Print usage instructions
     */
    private static void printUsage() {
        System.out.println("\nUsage: ExportCommand [OPTIONS]");
        System.out.println("\nExport Options (choose one):");
        System.out.println("  --zone <name>         Export specific zone");
        System.out.println("  --pattern <pattern>   Export records matching pattern (use * for wildcard)");
        System.out.println("  --all                 Export all DNS records");
        System.out.println("\nOutput Options:");
        System.out.println("  --output <file>       Output filename (default: auto-generated)");
        System.out.println("  --quick               Skip network validation (fast backup mode)");
        System.out.println("  --split               Create separate files by category");
        System.out.println("  --help                Show this help message");
        System.out.println("\nExport Modes:");
        System.out.println("  SMART (default):  Network analysis + status + recommendations");
        System.out.println("                    Takes 5-8s, provides actionable insights");
        System.out.println("  QUICK (--quick):  No analysis, just data export");
        System.out.println("                    Takes <1s, for backups/migration only");
        System.out.println("\nExamples:");
        System.out.println("  # Smart export with analysis (recommended)");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.ExportCommand --zone prod.local");
        System.out.println("");
        System.out.println("  # Quick backup (no analysis)");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.ExportCommand --zone prod.local --quick");
        System.out.println("");
        System.out.println("  # Export with categorized files");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.ExportCommand --zone prod.local --split");
        System.out.println("");
        System.out.println("  # Export by pattern");
        System.out.println("  java -cp \"lib/*:src\" com.dnsmanager.commands.ExportCommand --pattern \"web-*\" --output web-servers.csv");
    }
}
