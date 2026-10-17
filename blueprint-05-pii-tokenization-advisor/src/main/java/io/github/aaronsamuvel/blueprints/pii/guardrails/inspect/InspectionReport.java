package io.github.aaronsamuvel.blueprints.pii.guardrails.inspect;

import java.util.List;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.PrivacyAudit;
import io.github.aaronsamuvel.blueprints.pii.guardrails.vault.ResponsePolicy;

/**
 * Output of {@code POST /api/v1/privacy/inspect-roundtrip}: raw input, what the model saw, what
 * each tool was given, and the final answer, with a verdict on the LLM boundary.
 */
public record InspectionReport(
        String rawInput,
        List<String> rawDocuments,
        List<String> callerAuthorities,
        ResponsePolicy responsePolicy,
        List<DetectedEntity> detections,
        List<String> toolsOfferedToLlm,
        List<String> toolsHiddenFromLlm,
        List<List<String>> llmVisiblePrompts,
        List<String> llmAnswers,
        List<PrivacyAudit.ToolDisclosure> toolDisclosures,
        String finalOutput,
        boolean blocked,
        List<String> notes,
        BoundaryVerdict verdict) {

    public record DetectedEntity(PiiEntityType type, String analyzer, String placeholder) {}

    /**
     * {@code zeroRawPiiCrossedLlmBoundary} is true when no protected raw value appears in any text
     * the model received, and re-running every analyzer over that text finds no further PII. It
     * can only vouch for PII that at least one analyzer recognises.
     */
    public record BoundaryVerdict(int protectedValues, int modelCalls, List<PiiEntityType> rawValueTypesSeenByLlm,
                                  List<PiiEntityType> unprotectedPiiSeenByLlm, boolean zeroRawPiiCrossedLlmBoundary) {}
}
