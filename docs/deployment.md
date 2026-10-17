# Deployment

## Free local deployment (all seven apps)

The handbook's Blueprint 04 Route A uses `azd up` on Azure. That needs an Azure subscription, so
the repo's free path is Docker Compose on your own machine:

```bash
docker compose -f docker-compose.apps.yml up --build -d          # apps only (stub/Ollama models, H2)
docker compose -f docker-compose.ai-support.yml -f docker-compose.apps.yml up --build -d   # plus pgvector, Redis, ...
docker compose -f docker-compose.apps.yml ps
docker compose -f docker-compose.apps.yml down
```

| Service | Module | Port |
|---|---|---|
| b01 | blueprint-01-goap-finops-incident-agent | 8081 |
| b02 | blueprint-02-langgraph4j-hitl-evaluation | 8082 |
| b03 | blueprint-03-modular-rag | 8083 |
| b04 | blueprint-04-transactional-swarm | 8084 |
| b05 | blueprint-05-pii-tokenization-advisor | 8085 |
| b06 | blueprint-06-mcp-mesh-model-switchboard | 8086 |
| b07 | blueprint-07-a2a-nl2sql-agent | 8087 |

- One multi-stage `Dockerfile` builds any module (`--build-arg MODULE=<module>`): a Maven 3.9 /
  Temurin 21 build stage, then a Temurin 21 JRE runtime running as a non-root user.
- Apps that call a model reach the host's Ollama at `host.docker.internal:11434`
  (override with `OLLAMA_BASE_URL`). No API keys are needed; nothing paid is used.
- B03, B05 and B06 also expose `/actuator/health`.
- Host port taken (for example by another app on 8081)? Override it per app with `B0N_HOST_PORT`,
  e.g. `$env:B01_HOST_PORT = 18081` in PowerShell before `up`. The container port stays 808N.
- Blueprint 06 needs `BLUEPRINTS_DEV_JWT_SECRET` (32+ random characters) in the shell that runs
  `up`; compose passes it through and nothing is written to a file. Mint a token inside the container:
  `docker compose -f docker-compose.apps.yml exec -T b06 java -cp /app/app.jar -Dloader.main=io.github.aaronsamuvel.blueprints.mcp.security.DevJwtMinter org.springframework.boot.loader.launch.PropertiesLauncher models.use models.premium`
- **Verified on the owner's Windows PC (17 Oct 2026, IST, Docker Desktop):** `up --build -d` built all
  seven images (520-622 MB each, sharing the JRE base layers) and started all seven containers. Results:
  B01 investigate `goalAchieved: true` and the billing CSV escalated batch-etl (₹6,400 against ₹803.33);
  B02 `scripts/smoke-test-workflows.sh` passed 5 of 5 checks; B03 `/actuator/health` UP and `/api/v1/rag/query`
  answered with citations; B04 placed order 3000 (2 × SKU-MSE-04, ₹3,998.00); B05 health UP and the
  inspect-roundtrip tokenised the person and account number; B06 health UP, 401 without a token, `PREMIUM`
  with `models.premium`, downgraded to `LOCAL_SMART` with `models.use` only; B07 answered the revenue
  question (3 rows) and served its agent card. `down` removed every container and the network.

## Azure (optional)

`azd up` (Route A) is optional and not part of this repo. It needs an Azure account; even the free
tier asks for a credit card. The same images can be pushed to Azure Container Apps if you choose to.
