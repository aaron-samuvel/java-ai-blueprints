# Overview

**java-ai-blueprints** is a hands-on companion to the handbook *Enterprise Java & Spring Boot AI
Systems*. The handbook has four parts:

1. **Part I:** the J-AIDLC (7 stages), five Java pillars and a framework matrix → `part-1-lifecycle/`
2. **Part II:** ten open-source GitHub repositories worth studying → `part-2-github-gems/` (catalogue as tested data, with licences)
3. **Part III:** seven architecture blueprints → `blueprint-01…07/`
4. **Part IV:** FinOps budget advisor, JUnit evaluation gates, TOON, AGENTS.md → `pillar-*/`, `AGENTS.md`

## Principles *(author's design, not from handbook)*
- **Runs offline:** `mvn verify` needs only a JDK and Maven. Models are stubbed deterministically.
- **Free and local:** Ollama and Postgres/pgvector instead of paid cloud models or Cosmos DB.
- **Honest:** claims we couldn't verify (TOON savings, for example) are labelled. See [version-notes.md](version-notes.md).
- **Same shape everywhere:** each topic folder has README, theory, examples, exercises, project and tests.

## Who it is for
Java and Spring developers moving into AI engineering who want patterns they can show in production and in a portfolio.

## Source caveat
The original PDF wasn't available while this repo was built. The content comes from reading notes.
See the note in [pdf-requirements-mapping.md](pdf-requirements-mapping.md).
