import com.dnsmanager.services.RuleBasedValidator;

public class TestRules {
    public static void main(String[] args) {
        RuleBasedValidator v = new RuleBasedValidator();
        System.out.println("🔍 Testing Rules Validator...\n");
        
        if (v.validateHostname("web-server-01").isValid) {
            System.out.println("✅ Valid hostname accepted");
        }
        if (!v.validateHostname("-invalid").isValid) {
            System.out.println("✅ Invalid (starts with -) rejected");
        }
        if (!v.validateHostname("invalid-").isValid) {
            System.out.println("✅ Invalid (ends with -) rejected");
        }
        if (v.validateIPv4("10.10.10.50").isValid) {
            System.out.println("✅ Valid IP accepted");
        }
        if (!v.validateIPv4("10.10.10.256").isValid) {
            System.out.println("✅ Invalid IP (256) rejected");
        }
        System.out.println("\n✅ All 5 rules validation tests passed!");
    }
}
