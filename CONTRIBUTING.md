# Contributing

Thanks for helping! Read [AGENTS.md](AGENTS.md) first. The same rules apply to people and AI agents.

## Workflow
1. Fork, then create a branch: `feat/<module>-<topic>` or `fix/<module>-<topic>`.
2. Keep each module self-contained: `README.md`, `theory.md`, `examples/`, `exercises.md`, `src/` (project and tests).
3. Run `./scripts/verify.sh` (or `verify.ps1`) and `python3 scripts/check-links.py`. Both must pass **without Docker or a
   live model**. CI (`.github/workflows/ci.yml`) runs the same checks on every push and pull request.
4. Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(blueprint-03): …`, `docs: …`, `test: …`.
5. Update `CHANGELOG.md`, `docs/project-progress.md` and `docs/pdf-requirements-mapping.md`.
6. Open a PR describing what you changed and how you tested it. Paste real test output only.

## Rules
- **No secrets.** Read keys from environment variables. Never commit `.env` files, tokens or passwords.
- **Deterministic tests.** Unit tests use `StubChatModel`/`StubEmbeddingModel`. Tests that need
  Docker, Testcontainers or Ollama are named `*IT` and run only with `-Pit`.
- **No paid services** in default config. Prefer Ollama and Postgres/pgvector.
- Java 21, four-space indent (see `.editorconfig`), records for DTOs and model contracts.
- Never force-push to `main`.
