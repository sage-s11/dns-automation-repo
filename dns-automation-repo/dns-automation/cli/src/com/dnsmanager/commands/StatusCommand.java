package com.dnsmanager.commands;

import com.dnsmanager.services.DnsServerService;

public class StatusCommand implements Command {
    private final DnsServerService serverService;
    
    public StatusCommand(DnsServerService serverService) {
        this.serverService = serverService;
    }
    
    @Override
    public void execute(String[] args) throws Exception {
        System.out.println("Checking BIND server status...\n");
        serverService.printStatus();
    }
    
    @Override
    public String getUsage() {
        return "status - Check BIND server status";
    }
}
