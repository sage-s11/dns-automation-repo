package com.dnsmanager.utils;

import java.util.regex.Pattern;

public class ValidationUtils {
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern HOSTNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$");

    public static boolean isValidIPv4(String ip) {
        if (!IPV4_PATTERN.matcher(ip).matches()) {
            return false;
        }
        
        String[] octets = ip.split("\\.");
        for(String octet : octets) {
            int value = Integer.parseInt(octet);
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidHostname(String hostname) {
        return hostname != null && !hostname.isEmpty() && hostname.length() <= 63 
            && HOSTNAME_PATTERN.matcher(hostname).matches();
    }

    public static boolean isValidIpAddress(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            for(String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
