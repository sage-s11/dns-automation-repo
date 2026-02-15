package com.dnsmanager.commands;

/**
 * Command interface following the Command pattern.
 * Each DNS operation implements this interface.
 */
public interface Command {
    /**
     * Execute the command with given arguments
     * @param args Command arguments (excluding command name)
     * @throws Exception if command execution fails
     */
    void execute(String[] args) throws Exception;
    
    /**
     * Get command usage/help text
     * @return Usage description
     */
    String getUsage();
}
