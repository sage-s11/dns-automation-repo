-- DNS Management System - Complete DDI Schema
-- Supports: DNS (all record types), DHCP, IPAM

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- ZONES TABLE
-- ============================================================================
CREATE TABLE zones (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    type VARCHAR(20) DEFAULT 'master',
    ns_ip INET NOT NULL,
    serial BIGINT NOT NULL,
    refresh INTEGER DEFAULT 3600,
    retry INTEGER DEFAULT 1800,
    expire INTEGER DEFAULT 604800,
    minimum_ttl INTEGER DEFAULT 86400,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_zones_name ON zones(name);

-- ============================================================================
-- DNS RECORDS TABLE (All types: A, AAAA, CNAME, MX, TXT, SRV, PTR, etc.)
-- ============================================================================
CREATE TABLE dns_records (
    id SERIAL PRIMARY KEY,
    zone_id INTEGER REFERENCES zones(id) ON DELETE CASCADE,
    hostname VARCHAR(255) NOT NULL,
    type VARCHAR(10) NOT NULL,
    value TEXT NOT NULL,
    ttl INTEGER DEFAULT 86400,
    priority INTEGER,          -- For MX, SRV records
    weight INTEGER,            -- For SRV records
    port INTEGER,              -- For SRV records
    is_primary BOOLEAN DEFAULT false,  -- For PTR management (multiple A → one IP)
    data JSONB,                -- Flexible storage for complex records
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT unique_hostname_type UNIQUE(zone_id, hostname, type)
);

CREATE INDEX idx_dns_records_zone ON dns_records(zone_id);
CREATE INDEX idx_dns_records_hostname ON dns_records(hostname);
CREATE INDEX idx_dns_records_type ON dns_records(type);
CREATE INDEX idx_dns_records_value ON dns_records(value);

-- ============================================================================
-- SUBNETS TABLE (IPAM)
-- ============================================================================
CREATE TABLE subnets (
    id SERIAL PRIMARY KEY,
    network CIDR UNIQUE NOT NULL,
    gateway INET,
    vlan_id INTEGER,
    description TEXT,
    location VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_subnets_network ON subnets USING GIST(network inet_ops);

-- ============================================================================
-- IP ALLOCATIONS TABLE (IPAM)
-- ============================================================================
CREATE TABLE ip_allocations (
    id SERIAL PRIMARY KEY,
    ip_address INET UNIQUE NOT NULL,
    subnet_id INTEGER REFERENCES subnets(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'free',  -- free, allocated, reserved
    allocated_to VARCHAR(255),
    dns_record_id INTEGER REFERENCES dns_records(id) ON DELETE SET NULL,
    description TEXT,
    allocated_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_ip_allocations_ip ON ip_allocations(ip_address);
CREATE INDEX idx_ip_allocations_subnet ON ip_allocations(subnet_id);
CREATE INDEX idx_ip_allocations_status ON ip_allocations(status);

-- ============================================================================
-- DHCP SCOPES TABLE (Future DHCP support)
-- ============================================================================
CREATE TABLE dhcp_scopes (
    id SERIAL PRIMARY KEY,
    subnet_id INTEGER REFERENCES subnets(id) ON DELETE CASCADE,
    range_start INET NOT NULL,
    range_end INET NOT NULL,
    lease_time INTEGER DEFAULT 86400,  -- 24 hours
    dns_servers INET[],
    routers INET[],
    domain_name VARCHAR(255),
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================================
-- DHCP LEASES TABLE (Future DHCP support)
-- ============================================================================
CREATE TABLE dhcp_leases (
    id SERIAL PRIMARY KEY,
    ip_address INET NOT NULL,
    mac_address MACADDR NOT NULL,
    hostname VARCHAR(255),
    scope_id INTEGER REFERENCES dhcp_scopes(id) ON DELETE CASCADE,
    lease_start TIMESTAMP DEFAULT NOW(),
    lease_end TIMESTAMP,
    state VARCHAR(20) DEFAULT 'active',  -- active, expired, released
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(mac_address)
);

CREATE INDEX idx_dhcp_leases_ip ON dhcp_leases(ip_address);
CREATE INDEX idx_dhcp_leases_mac ON dhcp_leases(mac_address);
CREATE INDEX idx_dhcp_leases_state ON dhcp_leases(state);

-- ============================================================================
-- DHCP RESERVATIONS TABLE (Static DHCP mappings)
-- ============================================================================
CREATE TABLE dhcp_reservations (
    id SERIAL PRIMARY KEY,
    mac_address MACADDR UNIQUE NOT NULL,
    ip_address INET UNIQUE NOT NULL,
    hostname VARCHAR(255),
    scope_id INTEGER REFERENCES dhcp_scopes(id) ON DELETE CASCADE,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- ============================================================================
-- AUDIT LOG (Track all changes)
-- ============================================================================
CREATE TABLE audit_log (
    id SERIAL PRIMARY KEY,
    table_name VARCHAR(50) NOT NULL,
    record_id INTEGER NOT NULL,
    action VARCHAR(10) NOT NULL,  -- INSERT, UPDATE, DELETE
    old_data JSONB,
    new_data JSONB,
    changed_by VARCHAR(100),
    changed_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_audit_log_table ON audit_log(table_name);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_time ON audit_log(changed_at);

-- ============================================================================
-- FUNCTIONS & TRIGGERS
-- ============================================================================

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_modified_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to all tables with updated_at
CREATE TRIGGER update_zones_modtime BEFORE UPDATE ON zones
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_dns_records_modtime BEFORE UPDATE ON dns_records
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_subnets_modtime BEFORE UPDATE ON subnets
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

CREATE TRIGGER update_ip_allocations_modtime BEFORE UPDATE ON ip_allocations
    FOR EACH ROW EXECUTE FUNCTION update_modified_column();

-- ============================================================================
-- USEFUL VIEWS
-- ============================================================================

-- View: IP utilization by subnet
CREATE VIEW subnet_utilization AS
SELECT 
    s.id AS subnet_id,
    s.network,
    s.description,
    COUNT(ia.id) FILTER (WHERE ia.status = 'allocated') AS allocated,
    COUNT(ia.id) FILTER (WHERE ia.status = 'free') AS free,
    COUNT(ia.id) FILTER (WHERE ia.status = 'reserved') AS reserved,
    COUNT(ia.id) AS total,
    ROUND(
        COUNT(ia.id) FILTER (WHERE ia.status = 'allocated')::NUMERIC / 
        NULLIF(COUNT(ia.id), 0) * 100, 
        2
    ) AS utilization_percent
FROM subnets s
LEFT JOIN ip_allocations ia ON ia.subnet_id = s.id
GROUP BY s.id, s.network, s.description;

-- View: All DNS records with zone names
CREATE VIEW dns_records_full AS
SELECT 
    dr.id,
    z.name AS zone_name,
    dr.hostname,
    dr.type,
    dr.value,
    dr.ttl,
    dr.priority,
    dr.is_primary,
    dr.created_at,
    dr.updated_at
FROM dns_records dr
JOIN zones z ON dr.zone_id = z.id;

-- ============================================================================
-- HELPER FUNCTIONS
-- ============================================================================

-- Function: Get next available IP in subnet
CREATE OR REPLACE FUNCTION next_available_ip(subnet_cidr CIDR)
RETURNS INET AS $$
DECLARE
    next_ip INET;
BEGIN
    SELECT ip INTO next_ip
    FROM generate_series(
        host(network(subnet_cidr))::inet + 1,
        host(broadcast(subnet_cidr))::inet - 1,
        '1'::inet
    ) AS ip
    WHERE ip NOT IN (
        SELECT ip_address FROM ip_allocations 
        WHERE ip_address << subnet_cidr
    )
    LIMIT 1;
    
    RETURN next_ip;
END;
$$ LANGUAGE plpgsql;

-- Function: Auto-increment zone serial
CREATE OR REPLACE FUNCTION increment_zone_serial(zone_name_param VARCHAR)
RETURNS BIGINT AS $$
DECLARE
    new_serial BIGINT;
BEGIN
    UPDATE zones 
    SET serial = serial + 1
    WHERE name = zone_name_param
    RETURNING serial INTO new_serial;
    
    RETURN new_serial;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- SAMPLE DATA (Optional - comment out if not needed)
-- ============================================================================

-- You can add sample data here for testing
-- Will be populated by migration script from existing zone files
