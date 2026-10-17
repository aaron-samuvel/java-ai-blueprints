package io.github.aaronsamuvel.blueprints.pii.guardrails.tools;

import java.util.Map;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Demo tools over synthetic banking data. Every name, number and address here is made up. */
@Component
public class BankingTools {

    private static final Map<String, String> ACCOUNTS = Map.of(
            "123456789012", "holder Priya Sharma, priya.sharma@example.com, balance INR 1,20,000.00",
            "987654321098", "holder Rahul Verma, rahul.v@example.org, balance INR 8,450.50");

    private static final Map<String, String> CONTACTS = Map.of(
            "priya sharma", "priya.sharma@example.com, +91 98765 43210",
            "rahul verma", "rahul.v@example.org, 9123456789");

    @Tool(description = "Balance and holder of a bank account")
    @ToolPrivacyPolicy(allowedPiiTypes = PiiEntityType.ACCOUNT_NUMBER, requiredRole = "ROLE_COMPLIANCE_AGENT")
    public String accountBalance(@ToolParam(description = "bank account number") String accountNumber) {
        String a = ACCOUNTS.get(accountNumber.replaceAll("\\s", ""));
        return a == null ? "no account matches " + accountNumber : "Account " + accountNumber + ": " + a;
    }

    @Tool(description = "Contact details of a customer by full name")
    @ToolPrivacyPolicy(allowedPiiTypes = PiiEntityType.PERSON, requiredRole = "ROLE_SUPPORT_AGENT")
    public String customerContact(@ToolParam(description = "customer's full name") String customerName) {
        String c = CONTACTS.get(customerName.strip().toLowerCase(java.util.Locale.ROOT));
        return c == null ? "no customer named " + customerName : customerName + ": " + c;
    }

    @Tool(description = "Opening hours of the branch in a city")
    @ToolPrivacyPolicy(allowedPiiTypes = PiiEntityType.LOCATION)
    public String branchHours(@ToolParam(description = "city") String city) {
        return "The " + city + " branch is open 09:30-16:00, Monday to Saturday";
    }
}
