# Changelog

All notable changes are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [SemVer](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-10-17 (foundation milestone, not yet published)

### Added
- Maven multi-module root on Java 21, Spring Boot 4.0.8 and Spring AI 2.0.1. Surefire runs the
  offline tests by default; failsafe runs the live Ollama tests under the `it` profile.
- `shared/`: deterministic `StubChatModel`, `StubEmbeddingModel`, `AiAnswer` record, `StubModelConfiguration`.
- `part-1-lifecycle/`: J-AIDLC theory, record output contract, `AuditTrailAdvisor`, virtual-thread fan-out.
- `blueprint-05-pii-tokenization-advisor/`: `PiiTokenizationAdvisor`, `TokenVault`,
  `@ToolPrivacyPolicy`, `PrivacyEnforcingToolCallback`, REST endpoint, tests and a live IT.
- Placeholder READMEs for blueprints 01–04, 06 and 07, plus the Part IV pillars; Part II catalogue
  with every repo link checked.
- Docs: overview, requirements mapping, architecture, setup, troubleshooting, glossary, version
  notes, progress. AGENTS.md, CONTRIBUTING.md, verify scripts.
