# Theory: zero-trust PII tokenisation

The handbook names this blueprint "Zero-trust PII tokenisation advisor with `@ToolPrivacyPolicy`".
The design below is **author's design (not from handbook)**, based on that title and on Spring AI's Advisor API.

## Threat model
Treat the model provider, prompt logs, traces and any advisor further down the chain as
**untrusted**. Personal data may leave the JVM only as opaque tokens.

| Leak path | Control |
|---|---|
| User prompt or system prompt sent to the model | `PiiTokenizationAdvisor` tokenises system and user messages |
| Tool output fed back to the model | `PrivacyEnforcingToolCallback` applies `@ToolPrivacyPolicy` |
| Logging or observability advisors | Tokenisation runs first (`HIGHEST_PRECEDENCE`), so they only see tokens |
| Model hallucinates a token that doesn't exist | `detokenize` leaves unknown tokens untouched and never invents values |
| Tool arguments containing tokens | The callback detokenises its input, so tools get real values while they run inside the JVM |

## Tokenise vs redact
- **Tokenise:** reversible. The model can still refer to "the customer's email", and the user sees
  the real value. Use it when the answer must contain the data.
- **Redact:** irreversible. Use it for data the model never needs, such as KYC identifiers.
- **Allow raw:** only for non-personal data, and the annotation's `reason` must justify it in code review.

## Why an Advisor?
The Advisor API (handbook pillar 3) is an around-interceptor on every `ChatClient` call. Putting the
control there means business code can't forget it. It's a cross-cutting concern, like a servlet filter.

## Limits
- Regex detection misses free-form PII such as names and addresses.
- Small models sometimes rewrite tokens (`<<EMAIL 1>>`). Keep the system prompt instruction, and
  consider validating the output.
- The vault lives in memory. Restarts lose the mappings, which is fine for one turn.
