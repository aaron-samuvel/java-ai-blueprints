#!/usr/bin/env bash
# Offline build + tests. Pass -it to also run live Ollama integration tests.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v java >/dev/null || { echo "JDK 21 not found on PATH"; exit 1; }
command -v mvn  >/dev/null || { echo "Maven not found on PATH"; exit 1; }
java -version 2>&1 | head -1
if [[ "${1:-}" == "-it" ]]; then
  mvn -B -Pit verify
else
  mvn -B verify
fi
