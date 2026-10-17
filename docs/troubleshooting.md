# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `release version 21 not supported` | Maven is using an older JDK | Set `JAVA_HOME` to JDK 21; check with `mvn -v` |
| `mvn: command not found` (box) | Maven is in user space, not on PATH | `export PATH=~/tools/apache-maven-3.9.16/bin:$PATH` |
| `-Pit` tests fail: `Connection refused :11434` | Ollama isn't running | Start Ollama; `ollama list`; set `OLLAMA_BASE_URL` |
| `model "llama3.2:3b" not found` | Model not pulled | `ollama pull llama3.2:3b` |
| Ollama very slow or out of memory | 8 GB RAM | Close other apps; use a 3B model; increase the request timeout |
| Port 808x in use (for example 8081) | Another app holds it | Set `PORT` first: `$env:PORT = 18081` (PowerShell) or `PORT=18081 mvn … spring-boot:run`, and call that port |
| Postgres connection refused on 5432 | This setup uses **15432** | `PGPORT=15432` |
| Answer shows `<<EMAIL_1>>` literally | The model changed or invented a token | Expected if the token is unknown (it's never invented); strengthen the system prompt |
| Testcontainers: `Could not find a valid Docker environment` | Docker isn't running | Start Docker Desktop; only needed for `-Pit` |
| Two `ChatModel` beans conflict | Stub plus Ollama both active | Set `BLUEPRINTS_MODEL=ollama` when the Ollama starter is on the classpath |
| `spring-boot:run` fails on `java-ai-blueprints` with `Unable to find a suitable main class` | Older checkout: `-am` also ran the goal on the parent POM | Pull: the parent and the libraries now set `spring-boot.run.skip=true` |
| Blueprint 06 won't start: no `JwtDecoder` bean | `BLUEPRINTS_DEV_JWT_SECRET` is not set (or the checkout predates `ee419d4`) | Set it to 32+ random characters in the shell before `spring-boot:run` |
| Blueprint 06 answers `401` | No token, or a token signed with another secret | Mint one with `DevJwtMinter` using the same `BLUEPRINTS_DEV_JWT_SECRET` (see the module README) |
| Blueprint 06 answers `403` | Token lacks the scope | `/api/switchboard/**` needs `models.use`; `/mcp` needs `mcp.tools` |
| Blueprint 03 jar fails with `no parser for ` | Checkout predates `ee06b5e` (jar directory entries were ingested) | Pull and rebuild |
| `-Pit`: `OllamaJudgeIT` relevancy fails on correct answers | Small judges answer "Yes." or reject Spring AI's default prompt | Already handled by `VerdictNormalizingChatModel` and `SMALL_JUDGE_RELEVANCY` (`2433c55`, `e53f8b3`) |
| `-Pit`: `GitHubCatalogueIT` gets 403 | GitHub API rate limit (60/hour without a token) | Wait an hour, or see Part II exercise 5 |
