package com.dnsmanager.models;

import java.sql.Timestamp;

/**
 * DNS Record model - represents any DNS record type
 */
public class DnsRecord {
    private int id;
    private int zoneId;
    private String hostname;
    private RecordType type;
    private String value;
    private int ttl;
    private Integer priority;    // For MX, SRV
    private Integer weight;      // For SRV
    private Integer port;        // For SRV
    private boolean isPrimary;   // For PTR management
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    /**
     * DNS Record Types
     */
    public enum RecordType {
        A, AAAA, CNAME, MX, TXT, PTR, NS, SOA, SRV;
        
        public static RecordType fromString(String type) {
            return RecordType.valueOf(type.toUpperCase());
        }
    }
    
    // Constructors
    public DnsRecord() {
        this.ttl = 86400; // Default TTL
    }
    
    public DnsRecord(int zoneId, String hostname, RecordType type, String value) {
        this();
        this.zoneId = zoneId;
        this.hostname = hostname;
        this.type = type;
        this.value = value;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public int getZoneId() { return zoneId; }
    public void setZoneId(int zoneId) { this.zoneId = zoneId; }
    
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
    
    public RecordType getType() { return type; }
    public void setType(RecordType type) { this.type = type; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }
    
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    
    public boolean isPrimary() { return isPrimary; }
    public void setPrimary(boolean primary) { isPrimary = primary; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public String toString() {
        return String.format("DnsRecord{%s %s %s -> %s}", 
            hostname, type, ttl, value);
    }
}
