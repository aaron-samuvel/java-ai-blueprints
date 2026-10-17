# Pre-flight check for the supporting environment (Windows PowerShell 5.1 or PowerShell 7).
# Same checks as preflight-check.sh. Exits 1 if a REQUIRED check failed; optional services only WARN.
#
#   .\scripts\preflight-check.ps1           check only (creates the pgvector extension if a pg17 container runs)
#   .\scripts\preflight-check.ps1 -Pull     also run "ollama pull" for missing models (multi-GB downloads)
#   .\scripts\preflight-check.ps1 -Tier1    small-laptop model set: llama3.2:3b + nomic-embed-text
param([switch]$Pull, [switch]$Tier1)
$ErrorActionPreference = "Continue"
Set-Location (Join-Path $PSScriptRoot "..")

# Load .env (KEY=VALUE lines) into this process only; values are never printed.
if (Test-Path .env) {
  Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
      $v = $Matches[2].Trim().Trim('"')
      if (-not [Environment]::GetEnvironmentVariable($Matches[1])) { [Environment]::SetEnvironmentVariable($Matches[1], $v) }
    }
  }
}
function Env($name, $default) { $v = [Environment]::GetEnvironmentVariable($name); if ($v) { $v } else { $default } }

$script:Fails = 0; $script:Warns = 0
function Ok($what, $msg)   { Write-Host ("  [ OK ] {0,-28} {1}" -f $what, $msg) }
function Warn($what, $msg) { Write-Host ("  [WARN] {0,-28} {1}" -f $what, $msg); $script:Warns++ }
function Fail($what, $msg) { Write-Host ("  [FAIL] {0,-28} {1}" -f $what, $msg); $script:Fails++ }
function PortOpen($h, [int]$p) {
  $c = New-Object System.Net.Sockets.TcpClient
  try { $c.ConnectAsync($h, $p).Wait(2000) -and $c.Connected } catch { $false } finally { $c.Dispose() }
}
function HttpOk($url) {
  try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 -Uri $url | Out-Null; $true } catch { $false }
}

$ollama = Env "OLLAMA_BASE_URL" "http://localhost:11434"
$models = (Env "REQUIRED_MODELS" "llama3.2 qwen2.5-coder:7b nomic-embed-text") -split '\s+' | Where-Object { $_ }
if ($Tier1) { $models = @("llama3.2:3b", "nomic-embed-text") }

Write-Host "Layer 1: JVM and CLI tools"
if (Get-Command java -ErrorAction SilentlyContinue) {
  $line = (& java -version 2>&1 | ForEach-Object { "$_" } | Select-Object -First 1)
  if ("$line" -match 'version "(\d+)') { $jv = [int]$Matches[1]; if ($jv -ge 21) { Ok "java" "major $jv" } else { Fail "java" "need >= 21, found $jv" } }
  else { Fail "java" "could not read version: $line" }
} else { Fail "java" "not on PATH (need JDK 21+)" }
if (Get-Command mvn -ErrorAction SilentlyContinue) {
  $mv = (mvn -v 2>$null | Select-Object -First 1)
  if ("$mv" -match 'Apache Maven (\d+\.\d+\.\d+)') {
    if ([version]$Matches[1] -ge [version]"3.8.1") { Ok "mvn" $Matches[1] } else { Fail "mvn" "need >= 3.8.1, found $($Matches[1])" }
  } else { Fail "mvn" "could not read version" }
} else { Fail "mvn" "not on PATH (need Maven 3.8.1+)" }
if (Get-Command uvx -ErrorAction SilentlyContinue) { Ok "uvx" "found" } else { Warn "uvx" "not found (only needed for the Embabel project creator)" }
if (Get-Command npx -ErrorAction SilentlyContinue) { Ok "npx" "found" } else { Warn "npx" "not found (only needed for the MCP Inspector)" }

Write-Host "Layer 2: container runtime"
$dockerUp = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
  docker info *> $null
  if ($LASTEXITCODE -eq 0) {
    $dockerUp = $true
    $platform = (docker version --format '{{.Server.Platform.Name}}' 2>$null)
    if ("$platform" -match 'Docker Desktop (\d+\.\d+\.\d+)') {
      if ([version]$Matches[1] -ge [version]"4.43.2") { Ok "docker (Desktop)" $Matches[1] } else { Warn "docker (Desktop)" "$($Matches[1]) is older than 4.43.2" }
    } else { Ok "docker" "daemon reachable" }
  }
}
if (-not $dockerUp) { Warn "docker" "daemon not reachable (only needed for backing services and the live tests)" }

Write-Host "Layer 3: local models (Ollama at $ollama)"
$tags = $null
try { $tags = Invoke-RestMethod -TimeoutSec 5 -Uri "$ollama/api/tags" } catch { }
if ($tags) {
  Ok "ollama" "reachable"
  $names = @($tags.models | ForEach-Object { $_.name })
  foreach ($m in $models) {
    $present = if ($m.Contains(":")) { $names -contains $m } else { @($names | Where-Object { $_ -eq $m -or $_.StartsWith("$($m):") }).Count -gt 0 }
    if ($present) { Ok "model $m" "present" }
    elseif ($Pull -and (Get-Command ollama -ErrorAction SilentlyContinue)) {
      Write-Host "         pulling $m ..."; ollama pull $m
      if ($LASTEXITCODE -eq 0) { Ok "model $m" "pulled" } else { Fail "model $m" "pull failed" }
    } else { Warn "model $m" "missing (run: ollama pull $m, or re-run with -Pull)" }
  }
} else { Warn "ollama" "not reachable (offline stub models still work; live tests need it)" }

Write-Host "Layer 4/5: backing services (docker-compose.ai-support.yml)"
$pgHost = Env "PGHOST" "localhost"; $pgPort = [int](Env "PGPORT" (Env "PG_HOST_PORT" "5432"))
if (PortOpen $pgHost $pgPort) {
  Ok "postgres-pgvector" "${pgHost}:$pgPort open"
  $cid = $null
  if ($dockerUp) { $cid = (docker ps -qf "ancestor=pgvector/pgvector:pg17" 2>$null | Select-Object -First 1) }
  if ($cid) {
    docker exec $cid psql -q -U (Env "POSTGRES_USER" "springai") -d (Env "POSTGRES_DB" "ai_enterprise_db") -c "CREATE EXTENSION IF NOT EXISTS vector;" *> $null
    if ($LASTEXITCODE -eq 0) { Ok "pgvector extension" "present (docker exec)" } else { Warn "pgvector extension" "could not run CREATE EXTENSION in container $cid" }
  } else { Warn "pgvector extension" "not checked (no pgvector/pgvector:pg17 container)" }
} else { Warn "postgres-pgvector" "${pgHost}:$pgPort closed" }
$presidio = Env "PRESIDIO_HOST_PORT" "5002"; $nacos = Env "NACOS_HOST_PORT" "8848"; $redis = [int](Env "REDIS_HOST_PORT" "6379")
$grafana = Env "GRAFANA_HOST_PORT" "3000"; $otlp = [int](Env "OTLP_GRPC_HOST_PORT" "4317")
if (HttpOk "http://localhost:$presidio/health") { Ok "presidio-analyzer" "healthy" } else { Warn "presidio-analyzer" "not reachable on $presidio (optional, Blueprint 05)" }
if (HttpOk "http://localhost:$nacos/nacos/v1/console/health/readiness") { Ok "nacos-standalone" "ready" } else { Warn "nacos-standalone" "not reachable on $nacos (optional, Blueprint 07)" }
if (PortOpen "localhost" $redis) { Ok "redis-stack" "port $redis open" } else { Warn "redis-stack" "port $redis closed (optional, FinOps)" }
if (HttpOk "http://localhost:$grafana/api/health") { Ok "otel-lgtm (grafana)" "healthy" } else { Warn "otel-lgtm (grafana)" "not reachable on $grafana (optional, traces)" }
if (PortOpen "localhost" $otlp) { Ok "otel-lgtm (otlp grpc)" "port open" } else { Warn "otel-lgtm (otlp grpc)" "port $otlp closed (optional)" }

Write-Host ""
Write-Host "Summary: $script:Fails failed, $script:Warns warnings"
if ($script:Fails -gt 0) { exit 1 } else { exit 0 }
