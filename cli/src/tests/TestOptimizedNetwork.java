import com.dnsmanager.services.OptimizedNetworkValidator;
import com.dnsmanager.services.OptimizedNetworkValidator.*;

import java.util.*;

/**
 * TestOptimizedNetwork - Tests all network validation strategies.
 * 
 * Run: java -cp "../lib/*:." TestOptimizedNetwork
 */
public class TestOptimizedNetwork {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     OPTIMIZED NETWORK VALIDATOR TEST            ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // Test IPs
        List<String> testIps = Arrays.asList(
            "10.10.10.100",     // Private (RFC1918)
            "10.10.10.101",     // Private
            "10.10.10.102",     // Private
            "192.168.1.1",      // Private
            "172.16.0.1",       // Private
            "8.8.8.8",          // Google DNS (public, active)
            "1.1.1.1",          // Cloudflare (public, active)
            "10.99.99.99",      // Private, non-existent
            "203.0.113.1",      // Public, non-existent (TEST-NET-3)
            "127.0.0.1"         // Localhost
        );

        // === TEST 1: RFC1918 detection ===
        System.out.println("━━━ TEST 1: RFC1918 Detection ━━━");
        String[] rfc1918Tests = {
            "10.0.0.1", "10.255.255.255",
            "172.16.0.1", "172.31.255.255",
            "192.168.0.1", "192.168.255.255",
            "8.8.8.8", "1.1.1.1", "203.0.113.1"
        };
        for (String ip : rfc1918Tests) {
            boolean isPrivate = OptimizedNetworkValidator.isRFC1918(ip);
            System.out.printf("  %s → %s%n", ip, isPrivate ? "✅ PRIVATE" : "🌐 PUBLIC");
        }
        System.out.println();

        // === TEST 2: SKIP_PRIVATE mode (should be instant for private IPs) ===
        System.out.println("━━━ TEST 2: SKIP_PRIVATE mode (fast internal import) ━━━");
        ValidationConfig skipConfig = ValidationConfig.fast();
        OptimizedNetworkValidator skipValidator = new OptimizedNetworkValidator(skipConfig);
        
        long start = System.nanoTime();
        Map<String, ValidationResult> skipResults = skipValidator.validateBatch(testIps);
        long skipTime = (System.nanoTime() - start) / 1_000_000;
        skipValidator.shutdown();

        for (Map.Entry<String, ValidationResult> entry : skipResults.entrySet()) {
            System.out.println("  " + entry.getValue());
        }
        System.out.printf("  ⏱ Total: %dms%n%n", skipTime);

        // === TEST 3: FULL mode (original behavior) ===
        System.out.println("━━━ TEST 3: FULL mode (thorough) ━━━");
        ValidationConfig fullConfig = ValidationConfig.thorough();
        OptimizedNetworkValidator fullValidator = new OptimizedNetworkValidator(fullConfig);

        start = System.nanoTime();
        Map<String, ValidationResult> fullResults = fullValidator.validateBatch(testIps);
        long fullTime = (System.nanoTime() - start) / 1_000_000;
        fullValidator.shutdown();

        for (Map.Entry<String, ValidationResult> entry : fullResults.entrySet()) {
            System.out.println("  " + entry.getValue());
        }
        System.out.printf("  ⏱ Total: %dms%n%n", fullTime);

        // === TEST 4: NONE mode (disabled) ===
        System.out.println("━━━ TEST 4: NONE mode (disabled) ━━━");
        ValidationConfig noneConfig = ValidationConfig.disabled();
        OptimizedNetworkValidator noneValidator = new OptimizedNetworkValidator(noneConfig);

        start = System.nanoTime();
        Map<String, ValidationResult> noneResults = noneValidator.validateBatch(testIps);
        long noneTime = (System.nanoTime() - start) / 1_000_000;
        noneValidator.shutdown();

        System.out.printf("  All %d IPs marked safe in %dms%n%n", noneResults.size(), noneTime);

        // === COMPARISON ===
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║           STRATEGY COMPARISON                   ║");
        System.out.println("╠══════════════════════════════════════════════════╣");
        System.out.printf("║  SKIP_PRIVATE (--fast):   %5dms               ║%n", skipTime);
        System.out.printf("║  FULL (--thorough):       %5dms               ║%n", fullTime);
        System.out.printf("║  NONE (no validation):    %5dms               ║%n", noneTime);
        if (fullTime > 0) {
            System.out.printf("║  Speedup (fast vs full):  %.1fx faster          ║%n",
                (double) fullTime / Math.max(1, skipTime));
        }
        System.out.println("╚══════════════════════════════════════════════════╝");
    }
}
