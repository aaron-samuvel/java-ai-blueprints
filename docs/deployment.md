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
- Verified here: the Dockerfile's build command (`mvn -pl <module> -am package`) and the jar glob
  were run on the box, and the B05 jar booted and reported UP. The images themselves were **not**
  built here, because the box has no Docker. Run the compose command on a machine with Docker.

## Azure (optional)

`azd up` (Route A) is optional and not part of this repo. It needs an Azure account; even the free
tier asks for a credit card. The same images can be pushed to Azure Container Apps if you choose to.
