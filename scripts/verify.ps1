# Offline build + tests. Use -It to also run live Ollama integration tests.
param([switch]$It)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw "JDK 21 not found on PATH" }
if (-not (Get-Command mvn  -ErrorAction SilentlyContinue)) { throw "Maven not found on PATH" }
java -version
if ($It) { mvn -B -Pit verify } else { mvn -B verify }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
