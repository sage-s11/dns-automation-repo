package com.dnsmanager.services;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

/**
 * OptimizedNetworkValidator - Faster network validation with smart skipping.
 * 
 * OPTIMIZATIONS over EnterpriseNetworkValidator:
 * 1. RFC1918 smart skip — private IPs (10.x, 172.16-31.x, 192.168.x) skip validation
 * 2. Aggressive timeouts — 200ms first attempt (was 1000ms)
 * 3. Fewer retry attempts — 1 max (was 3)
 * 4. Fast-fail on common ports — checks 4 ports instead of 9+
 * 5. Configurable validation strategy
 * 
 * MODES:
 * - FULL:           TCP scan + ICMP (original behavior, most thorough)
 * - SKIP_PRIVATE:   Skip RFC1918 IPs entirely (best for internal imports)
 * - TCP_ONLY:       Skip ICMP (faster, less complete)
 * - NONE:           Skip all validation (trust CSV data)
 */
public class OptimizedNetworkValidator {

    // Fast scan: most common services only
    private static final int[] FAST_PORTS = {443, 80, 22, 3389};
    // Full scan: comprehensive
    private static final int[] FULL_PORTS = {443, 80, 22, 3389, 445, 3306, 5432, 8080, 8443};

    private final ValidationConfig config;
    private final ExecutorService executor;

    public OptimizedNetworkValidator(ValidationConfig config) {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.threadCount);
    }

    /**
     * Validate a batch of IPs in parallel. Returns results map.
     */
    public Map<String, ValidationResult> validateBatch(List<String> ips) {
        Map<String, ValidationResult> results = new ConcurrentHashMap<>();
        long startTime = System.nanoTime();

        // Deduplicate IPs
        List<String> uniqueIps = new ArrayList<>(new LinkedHashSet<>(ips));
        int skippedCount = 0;

        List<Future<?>> futures = new ArrayList<>();
        for (String ip : uniqueIps) {
            // Pre-filter: skip RFC1918 immediately if configured
            if (config.strategy == Strategy.SKIP_PRIVATE && isRFC1918(ip)) {
                results.put(ip, new ValidationResult(ip, RiskLevel.SAFE, "private-skip",
                    "RFC1918 private IP — skipped"));
                skippedCount++;
                continue;
            }
            if (config.strategy == Strategy.NONE) {
                results.put(ip, new ValidationResult(ip, RiskLevel.SAFE, "disabled",
                    "Validation disabled"));
                continue;
            }

            futures.add(executor.submit(() -> {
                results.put(ip, validateSingle(ip));
            }));
        }

        // Wait for network checks to complete
        for (Future<?> f : futures) {
            try {
                f.get(config.totalTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Timed out = not responding = safe
            } catch (Exception e) {
                // Ignore
            }
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000;

        long activeCount = results.values().stream().filter(r -> !r.isSafe()).count();
        System.out.printf("  ⚡ Network validation: %d IPs in %dms", uniqueIps.size(), elapsed);
        if (skippedCount > 0) System.out.printf(" (%d private skipped)", skippedCount);
        System.out.printf(" | %d active detected%n", activeCount);

        return results;
    }

    /**
     * Validate a single IP address.
     */
    private ValidationResult validateSingle(String ip) {
        // TCP port scan with aggressive timeout
        int[] ports = (config.strategy == Strategy.TCP_ONLY) ? FAST_PORTS : FULL_PORTS;

        for (int port : ports) {
            if (isTcpOpen(ip, port, config.firstAttemptTimeoutMs)) {
                return new ValidationResult(ip, RiskLevel.HIGH, "tcp:" + port,
                    "ACTIVE — TCP port " + port + " open");
            }
        }

        // Skip ICMP for TCP_ONLY mode
        if (config.strategy == Strategy.TCP_ONLY) {
            return new ValidationResult(ip, RiskLevel.SAFE, "tcp-clear",
                "No TCP response");
        }

        // ICMP ping
        if (icmpPing(ip, config.firstAttemptTimeoutMs)) {
            return new ValidationResult(ip, RiskLevel.MEDIUM, "icmp",
                "ACTIVE — responds to ICMP");
        }

        // One retry with longer timeout
        if (config.maxRetries > 0) {
            if (icmpPing(ip, config.firstAttemptTimeoutMs * 3)) {
                return new ValidationResult(ip, RiskLevel.MEDIUM, "icmp-retry",
                    "ACTIVE — responds to ICMP (retry)");
            }
        }

        return new ValidationResult(ip, RiskLevel.SAFE, "clear",
            "No network activity detected");
    }

    // ====================================================================
    // Network probe methods
    // ====================================================================

    private boolean isTcpOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean icmpPing(String ip, int timeoutMs) {
        try {
            int timeoutSec = Math.max(1, timeoutMs / 1000);
            String os = System.getProperty("os.name").toLowerCase();
            String[] cmd;
            if (os.contains("win")) {
                cmd = new String[]{"ping", "-n", "1", "-w", String.valueOf(timeoutMs), ip};
            } else {
                cmd = new String[]{"ping", "-c", "1", "-W", String.valueOf(timeoutSec), ip};
            }
            Process proc = Runtime.getRuntime().exec(cmd);
            boolean finished = proc.waitFor(timeoutMs + 500, TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return false;
            }
            return proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ====================================================================
    // Utility
    // ====================================================================

    /**
     * Check if IP is RFC1918 private address.
     * 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
     */
    public static boolean isRFC1918(String ip) {
        try {
            String[] octets = ip.split("\\.");
            if (octets.length != 4) return false;
            int first = Integer.parseInt(octets[0]);
            int second = Integer.parseInt(octets[1]);

            if (first == 10) return true;
            if (first == 172 && second >= 16 && second <= 31) return true;
            if (first == 192 && second == 168) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // ====================================================================
    // Enums, Config, Results
    // ====================================================================

    public enum Strategy { FULL, SKIP_PRIVATE, TCP_ONLY, NONE }

    public enum RiskLevel { SAFE, MEDIUM, HIGH }

    public static class ValidationConfig {
        public Strategy strategy = Strategy.FULL;
        public int threadCount = 30;
        public int firstAttemptTimeoutMs = 200;   // was 1000ms
        public int maxRetries = 1;                 // was 3
        public int totalTimeoutMs = 10_000;

        /** Fastest: skip private IPs, aggressive timeouts, no retries */
        public static ValidationConfig fast() {
            ValidationConfig c = new ValidationConfig();
            c.strategy = Strategy.SKIP_PRIVATE;
            c.firstAttemptTimeoutMs = 200;
            c.maxRetries = 0;
            c.threadCount = 40;
            return c;
        }

        /** Thorough: full TCP + ICMP with retries */
        public static ValidationConfig thorough() {
            ValidationConfig c = new ValidationConfig();
            c.strategy = Strategy.FULL;
            c.firstAttemptTimeoutMs = 500;
            c.maxRetries = 2;
            c.threadCount = 20;
            return c;
        }

        /** Internal imports: skip all private IPs */
        public static ValidationConfig internalOnly() {
            ValidationConfig c = new ValidationConfig();
            c.strategy = Strategy.SKIP_PRIVATE;
            c.firstAttemptTimeoutMs = 200;
            c.maxRetries = 0;
            c.threadCount = 30;
            return c;
        }

        /** No validation at all */
        public static ValidationConfig disabled() {
            ValidationConfig c = new ValidationConfig();
            c.strategy = Strategy.NONE;
            return c;
        }
    }

    public static class ValidationResult {
        private final String ip;
        private final RiskLevel risk;
        private final String method;
        private final String detail;

        public ValidationResult(String ip, RiskLevel risk, String method, String detail) {
            this.ip = ip;
            this.risk = risk;
            this.method = method;
            this.detail = detail;
        }

        public String getIp()      { return ip; }
        public RiskLevel getRisk()  { return risk; }
        public String getMethod()   { return method; }
        public String getDetail()   { return detail; }
        public boolean isSafe()     { return risk == RiskLevel.SAFE; }
        public boolean isBlocked()  { return risk == RiskLevel.HIGH; }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s) — %s", risk, ip, method, detail);
        }
    }
}
