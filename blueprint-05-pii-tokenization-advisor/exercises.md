# Exercises: Blueprint 05

*(author's design, not from the handbook)*

1. Add a `UPI` PII type (e.g. `name@okbank`) that doesn't clash with `EMAIL`. Write the failing test first.
2. Make the vault survive a multi-turn conversation by keying it on a conversation id stored in the advisor context.
3. Add `@ToolPrivacyPolicy(mode = HASH)`, which replaces values with a salted SHA-256. Read the salt from an env var, never from code.
4. Write a `StubChatModel` responder that emits `<<EMAIL 1>>` (mangled). Then make the advisor tolerate that variant.
5. Run `OllamaPiiIT` with `-Pit` and log what `llama3.2:3b` actually received. Confirm it contains only tokens.
