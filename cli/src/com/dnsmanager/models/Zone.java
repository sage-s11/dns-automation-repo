package com.dnsmanager.models;

import java.sql.Timestamp;

public class Zone {
    private int id;
    private String name;
    private String nsIp;
    private long serial;
    private int refresh = 3600;
    private int retry = 1800;
    private int expire = 604800;
    private int minimumTtl = 86400;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    
    public Zone() {}
    
    public Zone(String name, String nsIp, long serial) {
        this.name = name;
        this.nsIp = nsIp;
        this.serial = serial;
    }
    
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
}
