import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class DnsManager {

    private static final Path ZONE_FILE =
            Path.of("../../zones/db.examplenv.demo");

    private static final Pattern IPV4 =
            Pattern.compile("\\b(\\d{1,3}\\.){3}\\d{1,3}\\b");

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            usage();
            return;
        }

        switch (args[0]) {
            case "list" -> listRecords();
            case "add"  -> addRecord(args);
            default     -> usage();
        }
    }

    private static void usage() {
        System.out.println("Usage:");
        System.out.println("  list");
        System.out.println("  add <hostname> <ip>");
    }

    private static void listRecords() throws Exception {
        Files.readAllLines(ZONE_FILE).forEach(System.out::println);
    }

    private static void addRecord(String[] args) throws Exception {

        if (args.length != 3) {
            usage();
            return;
        }

        String host = args[1];
        String ip   = args[2];

        if (!IPV4.matcher(ip).matches()) {
            throw new IllegalArgumentException("Invalid IP address");
        }

        List<String> lines = Files.readAllLines(ZONE_FILE);
        List<String> updated = new ArrayList<>();

        for (String line : lines) {
            if (line.contains("Serial")) {
                updated.add(bumpSerial(line));
            } else {
                updated.add(line);
            }
        }

        updated.add(host + " IN A " + ip);

        Path tmp = Files.createTempFile("zone", ".tmp");
        Files.write(tmp, updated);

        Files.move(tmp, ZONE_FILE, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("Record added safely.");
    }

    private static String bumpSerial(String line) {
        String digits = line.replaceAll("\\D", "");
        long serial = Long.parseLong(digits) + 1;
        return "        " + serial + " ; Serial";
    }
}

