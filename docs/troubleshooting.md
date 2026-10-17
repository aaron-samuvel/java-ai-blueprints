# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `release version 21 not supported` | Maven is using an older JDK | Set `JAVA_HOME` to JDK 21; check with `mvn -v` |
| `mvn: command not found` (box) | Maven is in user space, not on PATH | `export PATH=~/tools/apache-maven-3.9.16/bin:$PATH` |
| `-Pit` tests fail: `Connection refused :11434` | Ollama isn't running | Start Ollama; `ollama list`; set `OLLAMA_BASE_URL` |
| `model "llama3.2:3b" not found` | Model not pulled | `ollama pull llama3.2:3b` |
| Ollama very slow or out of memory | 8 GB RAM | Close other apps; use a 3B model; increase the request timeout |
| Port 8085 in use | Another app | `PORT=8090 mvn … spring-boot:run` |
| Postgres connection refused on 5432 | This setup uses **15432** | `PGPORT=15432` |
| Answer shows `<<EMAIL_1>>` literally | The model changed or invented a token | Expected if the token is unknown (it's never invented); strengthen the system prompt |
| Testcontainers: `Could not find a valid Docker environment` | Docker isn't running | Start Docker Desktop; only needed for `-Pit` |
| Two `ChatModel` beans conflict | Stub plus Ollama both active | Set `BLUEPRINTS_MODEL=ollama` when the Ollama starter is on the classpath |
