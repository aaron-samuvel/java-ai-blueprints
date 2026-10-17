# Setup

## Requirements
| Tool | Needed for | Version |
|---|---|---|
| JDK | everything | 21 (Temurin recommended) |
| Maven | everything | 3.9+ |
| Ollama | `-Pit` live tests, running apps live | any recent; models `llama3.2:3b`, `nomic-embed-text` |
| Docker | future Testcontainers ITs | Docker Desktop / Engine |
| Postgres + pgvector | future blueprints 03/04 | 16+ |

## Windows PC (Aaron's machine)
Already installed: Temurin 21, Maven, Docker Desktop, Ollama (`llama3.2:3b`, `nomic-embed-text`),
Postgres on **port 15432** with user `dev`.

```powershell
git clone https://github.com/aaron-samuvel/java-ai-blueprints.git   # after it is published
cd java-ai-blueprints
.\scripts\verify.ps1          # offline tests
ollama list                   # confirm llama3.2:3b and nomic-embed-text
.\scripts\verify.ps1 -It      # live Ollama tests
```

Database connection settings come from environment variables. **Never put the password in a
file in this repo.**
```powershell
$env:PGHOST="localhost"; $env:PGPORT="15432"; $env:PGUSER="dev"
$env:PGPASSWORD = Read-Host "Postgres password"   # session only, not saved
```
The apps read `spring.datasource.password=${PGPASSWORD}`.

## Linux / macOS / the build box (no sudo)
The JDK and Maven can live in user space:
```bash
mkdir -p ~/tools && cd ~/tools
curl -sL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" | tar xz
curl -sL https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.tar.gz | tar xz
export JAVA_HOME=$(ls -d ~/tools/jdk-21*) PATH=$JAVA_HOME/bin:~/tools/apache-maven-3.9.16/bin:$PATH
./scripts/verify.sh
```

## Environment variables
| Variable | Default | Used by |
|---|---|---|
| `BLUEPRINTS_MODEL` | `stub` | `stub` = offline echo model; `ollama` = real model (build with `-Pit`) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama starter |
| `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | Ollama chat |
| `PORT` | `8085` | Blueprint 05 HTTP port |
| `PGHOST` `PGPORT` `PGUSER` `PGPASSWORD` | — | future DB blueprints |

## Hardware note
Aaron's PC has 8 GB RAM, so stick to ~3B-parameter models. Larger models will swap or fail.
