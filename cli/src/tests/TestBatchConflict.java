import com.dnsmanager.config.DatabaseConfig;
import com.dnsmanager.models.DnsRecord;
import com.dnsmanager.models.DnsRecord.RecordType;
import com.dnsmanager.services.BatchConflictChecker;
import com.dnsmanager.services.BatchConflictChecker.*;

import java.sql.*;
import java.util.*;

/**
 * TestBatchConflict - Tests batch conflict detection.
 * 
 * Run: java -cp "../lib/*:." TestBatchConflict
 */
public class TestBatchConflict {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       BATCH CONFLICT CHECKER TEST               ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        try {
            int zoneId = getZoneId("testzone5.local");
            if (zoneId == -1) {
                System.out.println("❌ Zone 'testzone5.local' not found.");
                return;
            }
            System.out.println("✅ Using zone: testzone5.local (id=" + zoneId + ")");
            System.out.println();

            BatchConflictChecker checker = new BatchConflictChecker();

            // === TEST 1: Check records that EXIST ===
            System.out.println("━━━ TEST 1: Records that already exist ━━━");
            // These were imported earlier in your tests
            List<DnsRecord> existing = Arrays.asList(
                new DnsRecord(zoneId, "test-web-01", RecordType.A, "10.10.10.100"),
                new DnsRecord(zoneId, "test-db-01", RecordType.A, "10.10.10.101"),
                new DnsRecord(zoneId, "test-app-01", RecordType.A, "10.10.10.102")
            );

            ConflictReport report1 = checker.checkBatch(existing);
            System.out.printf("  Conflicts found: %d (in %dms)%n", report1.getConflictCount(), report1.getElapsedMs());
            for (Map.Entry<Integer, ConflictDetail> entry : report1.getConflicts().entrySet()) {
                System.out.println("    Row " + entry.getKey() + ": " + entry.getValue());
            }
            System.out.println();

            // === TEST 2: Check records that DON'T exist ===
            System.out.println("━━━ TEST 2: Records that don't exist (should be clean) ━━━");
            List<DnsRecord> fresh = Arrays.asList(
                new DnsRecord(zoneId, "brand-new-01", RecordType.A, "10.50.50.1"),
                new DnsRecord(zoneId, "brand-new-02", RecordType.A, "10.50.50.2"),
                new DnsRecord(zoneId, "brand-new-03", RecordType.A, "10.50.50.3")
            );

            ConflictReport report2 = checker.checkBatch(fresh);
            System.out.printf("  Conflicts found: %d (in %dms)%n", report2.getConflictCount(), report2.getElapsedMs());
            System.out.println("  Expected: 0 conflicts ✅");
            System.out.println();

            // === TEST 3: Mixed batch (some exist, some don't) ===
            System.out.println("━━━ TEST 3: Mixed batch ━━━");
            List<DnsRecord> mixed = Arrays.asList(
                new DnsRecord(zoneId, "test-web-01", RecordType.A, "10.10.10.100"),  // exists
                new DnsRecord(zoneId, "new-server-01", RecordType.A, "10.60.60.1"),  // new
                new DnsRecord(zoneId, "test-db-01", RecordType.A, "10.10.10.101"),   // exists
                new DnsRecord(zoneId, "new-server-02", RecordType.A, "10.60.60.2")   // new
            );

            ConflictReport report3 = checker.checkBatch(mixed);
            System.out.printf("  Conflicts found: %d (in %dms)%n", report3.getConflictCount(), report3.getElapsedMs());
            for (Map.Entry<Integer, ConflictDetail> entry : report3.getConflicts().entrySet()) {
                System.out.println("    Row " + entry.getKey() + ": " + entry.getValue());
            }
            System.out.println("  Expected: 2 conflicts (rows 0, 2)");
            System.out.println();

            // === TEST 4: IP conflict check ===
            System.out.println("━━━ TEST 4: IP conflict detection ━━━");
            List<DnsRecord> ipConflicts = Arrays.asList(
                new DnsRecord(zoneId, "different-name", RecordType.A, "10.10.10.100"),  // IP exists
                new DnsRecord(zoneId, "another-name", RecordType.A, "10.99.99.99")      // IP doesn't exist
            );

            Map<Integer, ConflictDetail> ipReport = checker.checkIpConflicts(ipConflicts);
            System.out.printf("  IP conflicts found: %d%n", ipReport.size());
            for (Map.Entry<Integer, ConflictDetail> entry : ipReport.entrySet()) {
                System.out.println("    Row " + entry.getKey() + ": " + entry.getValue());
            }
            System.out.println();

            System.out.println("✅ All conflict checker tests complete!");

        } catch (Exception e) {
            System.out.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseConfig.close();
        }
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
}
