import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.models.DnsRecord.RecordType;
import com.dnsmanager.services.BatchInsertService;
import com.dnsmanager.services.BatchInsertService.BatchResult;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * TestBatchInsert - Benchmarks batch insert vs individual inserts.
 * 
 * Run: java -cp "../lib/*:." TestBatchInsert
 */
public class TestBatchInsert {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       BATCH INSERT PERFORMANCE TEST             ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        try {
            // Get testzone5.local zone_id
            int zoneId = getZoneId("testzone5.local");
            if (zoneId == -1) {
                System.out.println("❌ Zone 'testzone5.local' not found. Run zone setup first.");
                return;
            }
            System.out.println("✅ Using zone: testzone5.local (id=" + zoneId + ")");
            System.out.println();

            // Clean up any previous test records
            cleanupTestRecords(zoneId);

            // === TEST 1: Batch Insert (30 records) ===
            System.out.println("━━━ TEST 1: Multi-row Batch INSERT (30 records) ━━━");
            List<DnsRecord> records30 = generateRecords(zoneId, "batch-test", 30);

            BatchInsertService batchService = new BatchInsertService();
            BatchResult result30 = batchService.insertRecords(records30);

            System.out.println("  Result: " + result30);
            System.out.printf("  Throughput: %.1f records/second%n", result30.getRecordsPerSecond());
            System.out.println();

            // Clean up
            cleanupTestRecords(zoneId);

            // === TEST 2: JDBC Batch (30 records) ===
            System.out.println("━━━ TEST 2: JDBC addBatch/executeBatch (30 records) ━━━");
            List<DnsRecord> records30b = generateRecords(zoneId, "jdbc-test", 30);

            BatchResult resultJdbc = batchService.insertRecordsJdbcBatch(records30b);

            System.out.println("  Result: " + resultJdbc);
            System.out.printf("  Throughput: %.1f records/second%n", resultJdbc.getRecordsPerSecond());
            System.out.println();

            // Clean up
            cleanupTestRecords(zoneId);

            // === TEST 3: Individual Inserts baseline (30 records) ===
            System.out.println("━━━ TEST 3: Individual INSERTs baseline (30 records) ━━━");
            List<DnsRecord> records30c = generateRecords(zoneId, "indiv-test", 30);
            long individualStart = System.nanoTime();
            int individualCount = 0;

            try (Connection conn = DatabaseConfig.getDataSource().getConnection()) {
                String sql = "INSERT INTO dns_records (zone_id, hostname, type, value, ttl) "
                           + "VALUES (?, ?, ?, ?::inet, ?) "
                           + "ON CONFLICT (zone_id, hostname, type) DO NOTHING";
                for (DnsRecord rec : records30c) {
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, rec.getZoneId());
                        stmt.setString(2, rec.getHostname());
                        stmt.setString(3, rec.getType().name());
                        stmt.setString(4, rec.getValue());
                        stmt.setInt(5, rec.getTtl());
                        stmt.executeUpdate();
                        individualCount++;
                    }
                }
            }

            long individualMs = (System.nanoTime() - individualStart) / 1_000_000;
            System.out.printf("  Inserted: %d records in %dms%n", individualCount, individualMs);
            System.out.printf("  Throughput: %.1f records/second%n",
                individualMs > 0 ? individualCount * 1000.0 / individualMs : 0);
            System.out.println();

            // Clean up
            cleanupTestRecords(zoneId);

            // === TEST 4: Upsert (30 records — insert then update) ===
            System.out.println("━━━ TEST 4: Batch UPSERT (30 records) ━━━");
            List<DnsRecord> records30d = generateRecords(zoneId, "upsert-test", 30);

            // First insert
            batchService.insertRecords(records30d);
            // Now upsert (should update TTL)
            for (DnsRecord rec : records30d) {
                rec.setTtl(7200); // Change TTL to 7200
            }
            BatchResult upsertResult = batchService.upsertRecords(records30d);

            System.out.println("  Result: " + upsertResult);
            System.out.printf("  Throughput: %.1f records/second%n", upsertResult.getRecordsPerSecond());
            System.out.println();

            // Clean up
            cleanupTestRecords(zoneId);

            // === TEST 5: Duplicate handling ===
            System.out.println("━━━ TEST 5: Duplicate handling (ON CONFLICT DO NOTHING) ━━━");
            List<DnsRecord> records5 = generateRecords(zoneId, "dup-test", 5);
            batchService.insertRecords(records5); // Insert first time
            BatchResult dupResult = batchService.insertRecords(records5); // Insert again

            System.out.println("  Result: " + dupResult);
            System.out.println("  Expected: 0 inserted (all duplicates skipped)");
            System.out.println();

            // Clean up
            cleanupTestRecords(zoneId);

            // === COMPARISON SUMMARY ===
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║           PERFORMANCE COMPARISON                ║");
            System.out.println("╠══════════════════════════════════════════════════╣");
            System.out.printf("║  Multi-row INSERT:  %5dms  (%.0f rec/s)         ║%n",
                result30.getElapsedMs(), result30.getRecordsPerSecond());
            System.out.printf("║  JDBC Batch:        %5dms  (%.0f rec/s)         ║%n",
                resultJdbc.getElapsedMs(), resultJdbc.getRecordsPerSecond());
            System.out.printf("║  Individual INSERT: %5dms  (%.0f rec/s)         ║%n",
                individualMs, individualMs > 0 ? individualCount * 1000.0 / individualMs : 0);
            System.out.println("╠══════════════════════════════════════════════════╣");

            if (individualMs > 0 && result30.getElapsedMs() > 0) {
                System.out.printf("║  Speedup: %.1fx faster with batch insert       ║%n",
                    (double) individualMs / result30.getElapsedMs());
            }
            System.out.println("╚══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseConfig.close();
        }
    }

    private static List<DnsRecord> generateRecords(int zoneId, String prefix, int count) {
        List<DnsRecord> records = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            DnsRecord rec = new DnsRecord(zoneId, String.format("%s-%03d", prefix, i),
                RecordType.A, String.format("10.20.%d.%d", i / 256, i % 256));
            rec.setTtl(3600);
            records.add(rec);
        }
        return records;
    }

    private static int getZoneId(String zoneName) throws SQLException {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM zones WHERE name = ?")) {
            stmt.setString(1, zoneName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
            return -1;
        }
    }

    private static void cleanupTestRecords(int zoneId) throws SQLException {
        try (Connection conn = DatabaseConfig.getDataSource().getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM dns_records WHERE zone_id = ? AND "
                 + "(hostname LIKE 'batch-test-%' OR hostname LIKE 'jdbc-test-%' "
                 + "OR hostname LIKE 'indiv-test-%' OR hostname LIKE 'upsert-test-%' "
                 + "OR hostname LIKE 'dup-test-%')")) {
            stmt.setInt(1, zoneId);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                System.out.println("  🧹 Cleaned up " + deleted + " test records");
            }
        }
    }
}
