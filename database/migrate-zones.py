#!/usr/bin/env python3
"""
DNS Zone File to PostgreSQL Migration Script
Migrates existing BIND zone files to PostgreSQL database
"""

import psycopg2
import re
import os
from datetime import datetime

# Database configuration
DB_CONFIG = {
    'host': 'localhost',
    'database': 'dnsmanager',
    'user': 'dnsadmin',
    'password': 'root'  # Change if you used different password
}

# Paths
ZONES_DIR = '/home/sage-s/projects/dns-automation/zones'

def connect_db():
    """Connect to PostgreSQL database"""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        return conn
    except Exception as e:
        print(f"❌ Database connection failed: {e}")
        exit(1)

def parse_zone_file(zone_file_path, zone_name):
    """Parse BIND zone file and extract records"""
    
    records = []
    soa_data = {}
    
    with open(zone_file_path, 'r') as f:
        content = f.read()
    
    # Parse SOA record
    soa_pattern = r'@\s+IN\s+SOA\s+(\S+)\s+(\S+)\s+\(\s*(\d+)\s*;\s*Serial\s*(\d+)\s*;\s*Refresh\s*(\d+)\s*;\s*Retry\s*(\d+)\s*;\s*Expire\s*(\d+)\s*\)'
    soa_match = re.search(soa_pattern, content, re.MULTILINE | re.DOTALL)
    
    if soa_match:
        soa_data = {
            'nameserver': soa_match.group(1),
            'admin': soa_match.group(2),
            'serial': int(soa_match.group(3)),
            'refresh': int(soa_match.group(4)),
            'retry': int(soa_match.group(5)),
            'expire': int(soa_match.group(6)),
            'minimum': int(re.search(r'(\d+)\s*\)\s*;\s*Minimum', content).group(1))
        }
    
    # Parse NS records
    ns_pattern = r'^(@|[\w.-]+)\s+IN\s+NS\s+(\S+)'
    for match in re.finditer(ns_pattern, content, re.MULTILINE):
        hostname = match.group(1) if match.group(1) != '@' else ''
        value = match.group(2)
        records.append({
            'hostname': hostname,
            'type': 'NS',
            'value': value,
            'ttl': 86400
        })
    
    # Parse A records
    a_pattern = r'^([\w.-]+)\s+IN\s+A\s+(\d+\.\d+\.\d+\.\d+)'
    for match in re.finditer(a_pattern, content, re.MULTILINE):
        hostname = match.group(1)
        ip = match.group(2)
        records.append({
            'hostname': hostname,
            'type': 'A',
            'value': ip,
            'ttl': 86400,
            'is_primary': True  # First A record is primary for PTR
        })
    
    # Parse PTR records
    ptr_pattern = r'^(\d+)\s+IN\s+PTR\s+(\S+)'
    for match in re.finditer(ptr_pattern, content, re.MULTILINE):
        hostname = match.group(1)
        value = match.group(2)
        records.append({
            'hostname': hostname,
            'type': 'PTR',
            'value': value,
            'ttl': 86400
        })
    
    return soa_data, records

def get_ns_ip_from_zone(zone_file_path):
    """Extract nameserver IP from zone file"""
    with open(zone_file_path, 'r') as f:
        content = f.read()
    
    # Look for ns1 A record
    ns_pattern = r'ns1\s+IN\s+A\s+(\d+\.\d+\.\d+\.\d+)'
    match = re.search(ns_pattern, content)
    if match:
        return match.group(1)
    
    # Default fallback
    return '192.168.1.17'

def migrate_zone(conn, zone_name, zone_file_path):
    """Migrate a single zone to database"""
    
    print(f"\n📂 Migrating zone: {zone_name}")
    
    cursor = conn.cursor()
    
    try:
        # Parse zone file
        soa_data, records = parse_zone_file(zone_file_path, zone_name)
        ns_ip = get_ns_ip_from_zone(zone_file_path)
        
        if not soa_data:
            print(f"  ⚠️  No SOA record found, using defaults")
            soa_data = {
                'serial': int(datetime.now().strftime('%Y%m%d01')),
                'refresh': 3600,
                'retry': 1800,
                'expire': 604800,
                'minimum': 86400
            }
        
        # Insert zone
        cursor.execute("""
            INSERT INTO zones (name, ns_ip, serial, refresh, retry, expire, minimum_ttl)
            VALUES (%s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (name) DO UPDATE 
            SET serial = EXCLUDED.serial,
                updated_at = NOW()
            RETURNING id
        """, (
            zone_name,
            ns_ip,
            soa_data['serial'],
            soa_data.get('refresh', 3600),
            soa_data.get('retry', 1800),
            soa_data.get('expire', 604800),
            soa_data.get('minimum', 86400)
        ))
        
        zone_id = cursor.fetchone()[0]
        print(f"  ✅ Zone created (ID: {zone_id})")
        
        # Insert records
        record_count = 0
        for record in records:
            try:
                cursor.execute("""
                    INSERT INTO dns_records 
                    (zone_id, hostname, type, value, ttl, is_primary)
                    VALUES (%s, %s, %s, %s, %s, %s)
                    ON CONFLICT (zone_id, hostname, type) DO UPDATE
                    SET value = EXCLUDED.value,
                        updated_at = NOW()
                """, (
                    zone_id,
                    record.get('hostname', ''),
                    record['type'],
                    record['value'],
                    record.get('ttl', 86400),
                    record.get('is_primary', False)
                ))
                record_count += 1
            except Exception as e:
                print(f"  ⚠️  Failed to insert record: {e}")
        
        print(f"  ✅ Migrated {record_count} records")
        
        conn.commit()
        return True
        
    except Exception as e:
        print(f"  ❌ Migration failed: {e}")
        conn.rollback()
        return False

def main():
    """Main migration function"""
    
    print("🔄 DNS Zone Migration to PostgreSQL")
    print("=" * 50)
    
    # Connect to database
    conn = connect_db()
    print("✅ Connected to database")
    
    # Find all zone files
    zone_files = []
    for filename in os.listdir(ZONES_DIR):
        if filename.startswith('db.') and not filename.endswith('.jnl'):
            zone_name = filename[3:]  # Remove 'db.' prefix
            zone_file_path = os.path.join(ZONES_DIR, filename)
            zone_files.append((zone_name, zone_file_path))
    
    print(f"\n📋 Found {len(zone_files)} zone files to migrate")
    
    # Migrate each zone
    success_count = 0
    for zone_name, zone_file_path in zone_files:
        if migrate_zone(conn, zone_name, zone_file_path):
            success_count += 1
    
    # Summary
    print("\n" + "=" * 50)
    print(f"📊 Migration Summary:")
    print(f"  Total zones: {len(zone_files)}")
    print(f"  Successful: {success_count}")
    print(f"  Failed: {len(zone_files) - success_count}")
    
    # Show what's in database
    cursor = conn.cursor()
    cursor.execute("SELECT name, COUNT(dr.id) FROM zones z LEFT JOIN dns_records dr ON z.id = dr.zone_id GROUP BY z.name")
    
    print("\n📊 Database contents:")
    for row in cursor.fetchall():
        print(f"  {row[0]}: {row[1]} records")
    
    conn.close()
    print("\n✅ Migration complete!")

if __name__ == '__main__':
    main()
