package io.github.aaronsamuvel.blueprints.finops;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.client.ChatClient;

/** Offline demo: a tenant with a 300-token budget runs out after a few calls. */
public final class FinOpsDemo {

    private FinOpsDemo() {}

    public static void main(String[] args) {
        TokenBudgetLedger ledger = new TokenBudgetLedger(300);
        FinOpsBudgetAdvisor advisor = new FinOpsBudgetAdvisor(ledger, new TokenEstimator(), 50, PriceList.FREE);
        ChatClient client = ChatClient.builder(StubChatModel.echo()).defaultAdvisors(advisor).build();
        for (int i = 1; i <= 10; i++) {
            try {
                client.prompt().user("Summarise ticket " + i + " about a delayed refund").call().content();
                System.out.println("call " + i + " ok   " + ledger.snapshot("default"));
            } catch (BudgetExceededException e) {
                System.out.println("call " + i + " BLOCKED: " + e.getMessage());
                break;
            }
        }
    }
}
