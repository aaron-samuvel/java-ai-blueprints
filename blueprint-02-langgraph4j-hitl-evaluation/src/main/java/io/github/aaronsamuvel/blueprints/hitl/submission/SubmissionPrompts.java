package io.github.aaronsamuvel.blueprints.hitl.submission;

/** Prompt fragments shared by the nodes. The document is fenced so the model treats it as data. */
final class SubmissionPrompts {

    private SubmissionPrompts() {}

    static String document(String text) {
        return "<document>\n" + text + "\n</document>";
    }

    static String profileAndDocument(ReviewWorkflowState s) {
        return "Parsed profile: " + s.parsedProfileJson() + "\n" + document(s.rawDocumentText());
    }
}
