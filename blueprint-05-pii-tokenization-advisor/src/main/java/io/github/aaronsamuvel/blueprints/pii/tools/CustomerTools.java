package io.github.aaronsamuvel.blueprints.pii.tools;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Demo tools over fake in-memory customer data (all values are synthetic). */
@Component
public class CustomerTools {

    private static final Map<String, String> CUSTOMERS = Map.of(
            "C-1001", "Priya Sharma, priya.sharma@example.com, +91 98765 43210, PAN ABCDE1234F",
            "C-1002", "Rahul Verma, rahul.v@example.org, 9123456789, PAN PQRSX6789K");

    @Tool(description = "Look up a customer's contact profile by customer id")
    @ToolPrivacyPolicy(value = ToolPrivacyPolicy.Mode.TOKENIZE, reason = "contains contact details")
    public String customerProfile(@ToolParam(description = "customer id, e.g. C-1001") String customerId) {
        return CUSTOMERS.getOrDefault(customerId, "unknown customer");
    }

    @Tool(description = "Get the customer's KYC document summary")
    @ToolPrivacyPolicy(value = ToolPrivacyPolicy.Mode.REDACT, reason = "KYC identifiers must never reach the model")
    public String kycSummary(@ToolParam(description = "customer id") String customerId) {
        return "KYC verified for " + CUSTOMERS.getOrDefault(customerId, "unknown");
    }

    @Tool(description = "Number of open support tickets for a customer")
    @ToolPrivacyPolicy(value = ToolPrivacyPolicy.Mode.ALLOW_RAW, reason = "aggregate count, not personal data")
    public int openTickets(@ToolParam(description = "customer id") String customerId) {
        return CUSTOMERS.containsKey(customerId) ? 2 : 0;
    }
}
