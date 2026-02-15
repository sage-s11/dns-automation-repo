package com.dnsmanager.models;

import java.sql.Timestamp;

/**
 * Zone model - represents a DNS zone
 */
public class Zone {
    private int id;
    private String name;
    private String nsIp;
    private long serial;
    private int refresh;
    private int retry;
    private int expire;
    private int minimumTtl;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    // Constructors
    public Zone() {
        this.refresh = 3600;
        this.retry = 1800;
        this.expire = 604800;
        this.minimumTtl = 86400;
    }
    
    public Zone(String name, String nsIp, long serial) {
        this();
        this.name = name;
        this.nsIp = nsIp;
        this.serial = serial;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getNsIp() { return nsIp; }
    public void setNsIp(String nsIp) { this.nsIp = nsIp; }
    
    public long getSerial() { return serial; }
    public void setSerial(long serial) { this.serial = serial; }
    
    public int getRefresh() { return refresh; }
    public void setRefresh(int refresh) { this.refresh = refresh; }
    
    public int getRetry() { return retry; }
    public void setRetry(int retry) { this.retry = retry; }
    
    public int getExpire() { return expire; }
    public void setExpire(int expire) { this.expire = expire; }
    
    public int getMinimumTtl() { return minimumTtl; }
    public void setMinimumTtl(int minimumTtl) { this.minimumTtl = minimumTtl; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    @Override
    public String toString() {
        return String.format("Zone{name='%s', serial=%d, nsIp='%s'}", 
            name, serial, nsIp);
    }
}
