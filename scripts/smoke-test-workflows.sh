#!/usr/bin/env bash
# Smoke test for Blueprint 2's /api/v1/workflows API. Start the app first:
#   mvn -B -pl blueprint-02-langgraph4j-hitl-evaluation -am spring-boot:run
# then run:  scripts/smoke-test-workflows.sh [base-url]     (default http://localhost:8082)
# It submits the three sample documents, checks each lands on the expected exit, then resumes the
# paused one as a human reviewer. Needs bash and curl only (works in Git Bash on Windows).
set -euo pipefail
BASE="${1:-${BASE_URL:-http://localhost:8082}}"
DIR="$(cd "$(dirname "$0")/.." && pwd)/blueprint-02-langgraph4j-hitl-evaluation/src/main/resources/submissions"
fail=0

json_field() { # json_field <json> <field>: first string or boolean value of "field"
  printf '%s' "$1" | tr -d '\n' | sed -n "s/.*\"$2\":\(\"[^\"]*\"\|true\|false\|null\).*/\1/p" | tr -d '"'
}
json_escape() { sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e ':a;N;$!ba;s/\n/\\n/g' "$1"; }

start() { # start <sample> -> response JSON
  curl -sS -X POST "$BASE/api/v1/workflows/start" -H 'Content-Type: application/json' \
    -d "{\"submissionId\":\"SMOKE-$1\",\"documentText\":\"$(json_escape "$DIR/$1.md")\"}"
}

check() { # check <label> <actual> <expected>
  if [[ "$2" == "$3" ]]; then echo "PASS  $1: $2"; else echo "FAIL  $1: expected $3, got $2"; fail=1; fi
}

for pair in strong:AUTO_APPROVED weak:AUTO_REJECTED mixed:AWAITING_HUMAN; do
  sample="${pair%%:*}"; expected="${pair#*:}"
  response="$(start "$sample")"
  check "$sample" "$(json_field "$response" status)" "$expected"
  if [[ "$sample" == mixed ]]; then thread="$(json_field "$response" threadId)"; fi
done

state="$(curl -sS "$BASE/api/v1/workflows/$thread/state")"
check "mixed state before resume" "$(json_field "$state" awaitingHuman)" "true"
resumed="$(curl -sS -X POST "$BASE/api/v1/workflows/$thread/resume" -H 'Content-Type: application/json' \
  -d '{"approved":true,"reviewer":"smoke-test","comment":"approved by smoke test"}')"
check "mixed after resume" "$(json_field "$resumed" status)" "HUMAN_APPROVED"

if [[ $fail -eq 0 ]]; then echo "All workflow smoke checks passed against $BASE"; else echo "Some checks failed"; fi
exit $fail
