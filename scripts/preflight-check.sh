#!/usr/bin/env bash
# Pre-flight check for the supporting environment (companion guide, Part IV).
# Reports every layer, then exits 1 if a REQUIRED check failed. Optional services only WARN.
#
#   ./scripts/preflight-check.sh            check only (creates the pgvector extension if Postgres is reachable)
#   ./scripts/preflight-check.sh --pull     also run "ollama pull" for missing models (multi-GB downloads)
#   ./scripts/preflight-check.sh --tier1    small-laptop model set: llama3.2:3b + nomic-embed-text
#
# Reads .env from the repo root if present (never prints secret values).
set -uo pipefail
cd "$(dirname "$0")/.."

PULL=false; TIER1=false
for a in "$@"; do
  case "$a" in
    --pull) PULL=true ;;
    --tier1) TIER1=true ;;
    -h|--help) sed -n '2,9p' "$0"; exit 0 ;;
    *) echo "unknown option $a"; exit 2 ;;
  esac
done

if [[ -f .env ]]; then set -a; source ./.env; set +a; fi

OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
REQUIRED_MODELS="${REQUIRED_MODELS:-llama3.2 qwen2.5-coder:7b nomic-embed-text}"
$TIER1 && REQUIRED_MODELS="llama3.2:3b nomic-embed-text"

FAILS=0; WARNS=0
ok()   { printf '  [ OK ] %-28s %s\n' "$1" "$2"; }
warn() { printf '  [WARN] %-28s %s\n' "$1" "$2"; WARNS=$((WARNS+1)); }
fail() { printf '  [FAIL] %-28s %s\n' "$1" "$2"; FAILS=$((FAILS+1)); }

# version_ge A B: true when dotted version A >= B
version_ge() { [[ "$(printf '%s\n%s\n' "$2" "$1" | sort -V | head -1)" == "$2" ]]; }

port_open() { (exec 3<>"/dev/tcp/$1/$2") >/dev/null 2>&1; }
http_ok()   { curl -fsS -m 5 -o /dev/null "$1" 2>/dev/null; }

echo "Layer 1: JVM and CLI tools"
if command -v java >/dev/null; then
  jv=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
  if [[ "$jv" =~ ^[0-9]+$ ]] && (( jv >= 21 )); then ok java "major $jv"; else fail java "need >= 21, found '$jv'"; fi
else fail java "not on PATH (need JDK 21+)"; fi
if command -v mvn >/dev/null; then
  mv=$(mvn -v 2>/dev/null | head -1 | sed -E 's/Apache Maven ([0-9.]+).*/\1/')
  if version_ge "$mv" 3.8.1; then ok mvn "$mv"; else fail mvn "need >= 3.8.1, found $mv"; fi
else fail mvn "not on PATH (need Maven 3.8.1+)"; fi
command -v uvx >/dev/null && ok uvx "$(uvx --version 2>/dev/null | head -1)" || warn uvx "not found (only needed for the Embabel project creator)"
command -v npx >/dev/null && ok npx "$(npx --version 2>/dev/null)" || warn npx "not found (only needed for the MCP Inspector)"

echo "Layer 2: container runtime"
if command -v docker >/dev/null && docker info >/dev/null 2>&1; then
  platform=$(docker version --format '{{.Server.Platform.Name}}' 2>/dev/null)
  if [[ "$platform" == *"Docker Desktop"* ]]; then
    dv=$(sed -E 's/.*Docker Desktop ([0-9.]+).*/\1/' <<<"$platform")
    if version_ge "$dv" 4.43.2; then ok "docker (Desktop)" "$dv"; else warn "docker (Desktop)" "$dv is older than 4.43.2"; fi
  else
    ok docker "daemon reachable ($(docker version --format '{{.Server.Version}}' 2>/dev/null))"
  fi
else
  warn docker "daemon not reachable (only needed for backing services and the live tests)"
fi

echo "Layer 3: local models (Ollama at $OLLAMA_BASE_URL)"
if tags=$(curl -fsS -m 5 "$OLLAMA_BASE_URL/api/tags" 2>/dev/null); then
  ok ollama "reachable"
  for m in $REQUIRED_MODELS; do
    # "llama3.2" matches any llama3.2 tag; "qwen2.5-coder:7b" must match exactly
    if [[ "$m" == *:* ]]; then pat="\"name\":\"$m\""; else pat="\"name\":\"$m(:[^\"]*)?\""; fi
    if grep -Eq "$pat" <<<"$tags"; then ok "model $m" "present"
    elif $PULL && command -v ollama >/dev/null; then
      echo "         pulling $m ..."; if ollama pull "$m"; then ok "model $m" "pulled"; else fail "model $m" "pull failed"; fi
    else warn "model $m" "missing (run: ollama pull $m, or re-run with --pull)"; fi
  done
else
  warn ollama "not reachable (offline stub models still work; live tests need it)"
fi

echo "Layer 4/5: backing services (docker-compose.ai-support.yml)"
PGH="${PGHOST:-localhost}"; PGP="${PGPORT:-${PG_HOST_PORT:-5432}}"
if port_open "$PGH" "$PGP"; then
  ok "postgres-pgvector" "$PGH:$PGP open"
  sql="CREATE EXTENSION IF NOT EXISTS vector;"
  cid=$(command -v docker >/dev/null && docker ps -qf "ancestor=pgvector/pgvector:pg17" 2>/dev/null | head -1)
  if [[ -n "${cid:-}" ]]; then
    docker exec "$cid" psql -q -U "${POSTGRES_USER:-springai}" -d "${POSTGRES_DB:-ai_enterprise_db}" -c "$sql" >/dev/null 2>&1 \
      && ok "pgvector extension" "present (docker exec)" || warn "pgvector extension" "could not run CREATE EXTENSION in container $cid"
  elif command -v psql >/dev/null && [[ -n "${PGPASSWORD:-}" ]]; then
    psql -q -h "$PGH" -p "$PGP" -U "${PGUSER:-springai}" -d "${PGDATABASE:-ai_enterprise_db}" -c "$sql" >/dev/null 2>&1 \
      && ok "pgvector extension" "present (psql)" || warn "pgvector extension" "CREATE EXTENSION failed (check PG* variables)"
  else
    warn "pgvector extension" "not checked (no pg17 container and no psql + PGPASSWORD)"
  fi
else
  warn "postgres-pgvector" "$PGH:$PGP closed"
fi
http_ok "http://localhost:${PRESIDIO_HOST_PORT:-5002}/health" && ok presidio-analyzer "healthy" || warn presidio-analyzer "not reachable on ${PRESIDIO_HOST_PORT:-5002} (optional, Blueprint 05)"
http_ok "http://localhost:${NACOS_HOST_PORT:-8848}/nacos/v1/console/health/readiness" && ok nacos-standalone "ready" || warn nacos-standalone "not reachable on ${NACOS_HOST_PORT:-8848} (optional, Blueprint 07)"
port_open localhost "${REDIS_HOST_PORT:-6379}" && ok redis-stack "port ${REDIS_HOST_PORT:-6379} open" || warn redis-stack "port ${REDIS_HOST_PORT:-6379} closed (optional, FinOps)"
http_ok "http://localhost:${GRAFANA_HOST_PORT:-3000}/api/health" && ok "otel-lgtm (grafana)" "healthy" || warn "otel-lgtm (grafana)" "not reachable on ${GRAFANA_HOST_PORT:-3000} (optional, traces)"
port_open localhost "${OTLP_GRPC_HOST_PORT:-4317}" && ok "otel-lgtm (otlp grpc)" "port open" || warn "otel-lgtm (otlp grpc)" "port ${OTLP_GRPC_HOST_PORT:-4317} closed (optional)"

echo
echo "Summary: $FAILS failed, $WARNS warnings"
(( FAILS == 0 ))
