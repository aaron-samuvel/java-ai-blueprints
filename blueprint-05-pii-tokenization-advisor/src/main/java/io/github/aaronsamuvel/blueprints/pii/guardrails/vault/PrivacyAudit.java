package io.github.aaronsamuvel.blueprints.pii.guardrails.vault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.aaronsamuvel.blueprints.pii.guardrails.detect.PiiEntityType;

/**
 * Audit trail of one request: what the model was sent, what tools were offered or hidden, and
 * what was disclosed to each tool. Values are only kept where the inspector needs to show them.
 */
public final class PrivacyAudit {

    public record Detection(String placeholder, PiiEntityType type) {}

    public record ToolDisclosure(String tool, String argumentsSeenByLlm, String argumentsDisclosedToTool,
                                 List<PiiEntityType> restoredTypes, List<String> withheldPlaceholders,
                                 String resultReturnedToLlm, boolean denied) {}

    private final List<String> sanitizedModelInputs = new CopyOnWriteArrayList<>();
    private final List<ToolDisclosure> disclosures = new CopyOnWriteArrayList<>();
    private final List<String> toolsOffered = new CopyOnWriteArrayList<>();
    private final List<String> toolsHidden = new CopyOnWriteArrayList<>();
    private final List<String> notes = new CopyOnWriteArrayList<>();
    private volatile boolean blocked;

    public void modelInput(String text) {
        sanitizedModelInputs.add(text);
    }

    public void disclosure(ToolDisclosure d) {
        disclosures.add(d);
    }

    public void offered(String tool) {
        toolsOffered.add(tool);
    }

    public void hidden(String tool) {
        toolsHidden.add(tool);
    }

    public void note(String note) {
        notes.add(note);
    }

    public void block() {
        blocked = true;
    }

    public List<String> sanitizedModelInputs() {
        return List.copyOf(sanitizedModelInputs);
    }

    public List<ToolDisclosure> disclosures() {
        return List.copyOf(disclosures);
    }

    public List<String> toolsOffered() {
        return List.copyOf(toolsOffered);
    }

    public List<String> toolsHidden() {
        return List.copyOf(toolsHidden);
    }

    public List<String> notes() {
        return List.copyOf(notes);
    }

    public boolean blocked() {
        return blocked;
    }
}
