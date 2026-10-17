package io.github.aaronsamuvel.blueprints.swarm.commerce.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** A decided refund or dispute, with how many evaluator-optimizer rounds it took. */
@Table("refund_cases")
public record RefundCase(@Id Long id, String tenantId, String userId, Long orderId, Long orderItemId, int quantity,
                         BigDecimal amountInr, String decision, String rationale, int policyRounds, LocalDateTime createdAt) {
}
