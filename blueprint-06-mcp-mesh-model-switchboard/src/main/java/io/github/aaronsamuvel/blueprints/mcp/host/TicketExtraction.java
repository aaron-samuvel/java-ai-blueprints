package io.github.aaronsamuvel.blueprints.mcp.host;

/** Target record for structured extraction; its JSON schema is what the validation advisor checks. */
public record TicketExtraction(String category, String priority, String summary) {}
