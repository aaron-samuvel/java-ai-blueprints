package io.github.aaronsamuvel.blueprints.nl2sql.dataagent;

import java.util.List;
import java.util.Locale;

import io.github.aaronsamuvel.blueprints.shared.StubChatModel;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Deterministic stand-in for the SQL-writing model, scripted to show each loop behaviour:
 * <ul>
 *   <li>"revenue by product category": the first attempt has a column typo ({@code categry}); it is fixed after the DB error.</li>
 *   <li>"cancelled": the first attempt joins on the wrong column (no rows); it is fixed after the empty-result feedback.</li>
 *   <li>"top customers": a 3-table join aggregation that succeeds first time.</li>
 *   <li>"per month": a time series.</li>
 *   <li>"drop table": always answers {@code DROP TABLE}, so the guard must block every attempt.</li>
 *   <li>"other tenant": always filters on tenant_id, which the guard refuses.</li>
 * </ul>
 * Anything else gets a query on a column that does not exist, so the loop runs out of retries.
 */
public final class DataAgentDemoModel extends StubChatModel {

    public DataAgentDemoModel() {
        super(DataAgentDemoModel::answer);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return super.call(prompt);
    }

    static String answer(Prompt p) {
        List<Message> msgs = p.getInstructions();
        String user = msgs.stream().filter(UserMessage.class::isInstance).reduce((a, b) -> b).map(Message::getText).orElse("");
        String q = user.toLowerCase(Locale.ROOT);
        String question = q.lines().findFirst().orElse("");
        boolean retry = q.contains("previous attempts");
        if (question.contains("drop table")) return "DROP TABLE shop_orders";
        if (question.contains("other tenant")) return "SELECT SUM(l.quantity * l.unit_price_inr) AS revenue FROM shop_order_lines l WHERE l.tenant_id = 'globex'";
        if (question.contains("revenue by product category")) {
            String col = retry && q.contains("categry") ? "category" : "categry";
            return "SELECT p." + col + " AS category, SUM(l.quantity * l.unit_price_inr) AS revenue FROM shop_order_lines l "
                    + "JOIN shop_products p ON p.id = l.product_id GROUP BY p." + col + " ORDER BY revenue DESC";
        }
        if (question.contains("cancelled")) {
            String join = retry && q.contains("0 rows") ? "p.id = l.product_id" : "p.id = l.order_id";
            return "SELECT p.name AS product, SUM(l.quantity * l.unit_price_inr) AS lost_revenue FROM shop_orders o "
                    + "JOIN shop_order_lines l ON l.order_id = o.id JOIN shop_products p ON " + join
                    + " WHERE o.status = 'CANCELLED' GROUP BY p.name ORDER BY lost_revenue DESC";
        }
        if (question.contains("top customers")) {
            return "SELECT c.name AS customer, SUM(l.quantity * l.unit_price_inr) AS revenue FROM shop_customers c "
                    + "JOIN shop_orders o ON o.customer_id = c.id JOIN shop_order_lines l ON l.order_id = o.id "
                    + "WHERE c.city = 'Chennai' AND o.status <> 'CANCELLED' GROUP BY c.name ORDER BY revenue DESC LIMIT 5";
        }
        if (question.contains("per month")) {
            return "SELECT FORMATDATETIME(o.placed_on, 'yyyy-MM') AS order_month, COUNT(*) AS orders FROM shop_orders o "
                    + "GROUP BY FORMATDATETIME(o.placed_on, 'yyyy-MM') ORDER BY order_month";
        }
        return "SELECT o.unknown_column FROM shop_orders o";
    }
}
