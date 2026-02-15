package com.dnsmanager;

import com.dnsmanager.commands.*;
import com.dnsmanager.services.*;
import com.dnsmanager.zonecommands.*;
import java.nio.file.*;
import java.util.*;

/**
 * Main DNS Manager - Modular Architecture
 * 
 * This is the entry point that orchestrates all commands and services.
 */
public class DnsManager {
    // Configuration
    private static final Path ZONE_FILE = Paths.get("zones/db.examplenv.demo");
    private static final String ZONE_NAME = "examplenv.demo";
    private static final String CONTAINER_NAME = "bind9-demo";
    private static final int RNDC_PORT = 1953;
    private static final int MAX_BACKUPS = 10;
    
    // Services (shared across commands)
    private static ZoneFileService zoneService;
    private static DnsServerService serverService;
    private static ReverseZoneService reverseZoneService;
    private static BackupService backupService;
    private static BindConfigService bindConfigService;
    private static ZoneManagerService zoneManager;
    private static ZoneFileServiceDynamic zoneServiceDynamic;
    private static DnsServerServiceDynamic serverServiceDynamic;
    
    // Command registry
    private static Map<String, Command> commands;
    
    public static void main(String[] args) {
        try {
            // Initialize services
            initializeServices();
            // Zone management commands
        if (args.length > 0 && args[0].equals("zone")) {
            if (args.length < 2) {
                System.err.println("Usage: ./dns zone <add|list|delete|show> [options]");
                System.exit(1);
            }
            
            String zoneCommand = args[1];
            String[] zoneArgs = Arrays.copyOfRange(args, 2, args.length);
            
            switch (zoneCommand) {
                case "add":
                    new ZoneAddCommand(zoneManager).execute(zoneArgs);
                    break;
                case "list":
                    new ZoneListCommand(zoneManager).execute(zoneArgs);
                    break;
                case "delete":
                    new ZoneDeleteCommand(zoneManager).execute(zoneArgs);
                    break;
                case "show":
                    new ZoneShowCommand(zoneManager).execute(zoneArgs);
                    break;
                default:
                    System.err.println("Unknown zone command: " + zoneCommand);
                    System.exit(1);
            }
            return;
        }
            // Initialize commands
            initializeCommands();
            
            // Parse and execute command
            if (args.length == 0) {
                printUsage();
                return;
            }
            
            String commandName = args[0];
            Command command = commands.get(commandName);
            
            if (command == null) {
                System.err.println("Unknown command: " + commandName);
                printUsage();
                System.exit(1);
            }
            
            // Extract arguments (skip command name)
            String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
            
            // Execute command
            command.execute(commandArgs);
            
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Initialize all services
     */
    private static void initializeServices() {
        Path backupDir = ZONE_FILE.getParent().resolve("backups");
        
        zoneService = new ZoneFileService(ZONE_NAME);
        serverService = new DnsServerService(CONTAINER_NAME, ZONE_NAME, RNDC_PORT);
        backupService = new BackupService(backupDir, MAX_BACKUPS);
        zoneServiceDynamic = new ZoneFileServiceDynamic("zones");
        serverServiceDynamic = new DnsServerServiceDynamic(CONTAINER_NAME, RNDC_PORT);
        reverseZoneService = new ReverseZoneService("zones", zoneServiceDynamic);
    	bindConfigService = new BindConfigService("config/named.conf");
        zoneManager = new ZoneManagerService("zones", bindConfigService);

	}
    
    /**
     * Initialize all commands
     */
    private static void initializeCommands() {
        commands = new LinkedHashMap<>();

        // Updated commands with Dynamic services and --zone support
        commands.put("list", new ListCommand(zoneServiceDynamic, bindConfigService, ZONE_NAME));
        commands.put("add", new AddCommand(zoneServiceDynamic, serverServiceDynamic, backupService, reverseZoneService, ZONE_NAME));
        commands.put("update", new UpdateCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
        commands.put("set", new SetCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));
        commands.put("delete", new DeleteCommand(zoneServiceDynamic, serverServiceDynamic, backupService, ZONE_NAME));

        // Keep these commands as-is (they don't need zone flag support)
        commands.put("reload", new ReloadCommand(zoneService, serverService, ZONE_NAME));
        commands.put("validate", new ValidateCommand(zoneService, serverService));
        commands.put("status", new StatusCommand(serverService));
        commands.put("backup", new BackupCommand(zoneService, backupService));
    }
    
    /**
     * Print usage information
     */
    private static void printUsage() {
        System.out.println("DNS Manager - Modular CLI");
        System.out.println("\nUsage: dns <command> [arguments]");
        System.out.println("\nAvailable commands:");
        
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            System.out.println("  " + entry.getValue().getUsage());
        }
        
        System.out.println("\nExamples:");
        System.out.println("  dns list");
        System.out.println("  dns add www 10.10.10.50");
        System.out.println("  dns reload");
        System.out.println("  dns status");
    }
    
    /**
     * BackupCommand implementation (inner class for convenience)
     */
    private static class BackupCommand implements Command {
        private final ZoneFileService zoneService;
        private final BackupService backupService;
        
        public BackupCommand(ZoneFileService zoneService, BackupService backupService) {
            this.zoneService = zoneService;
            this.backupService = backupService;
        }
        
        @Override
        public void execute(String[] args) throws Exception {
            Path backup = backupService.createBackup(zoneService.getZoneFile());
            System.out.println("✓ Backup created: " + backup);
        }
        
        @Override
        public String getUsage() {
            return "backup - Create manual backup of zone file";
        }
    }
}
