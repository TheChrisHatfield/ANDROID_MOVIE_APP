# Ensure services/search/.env exists for PC runs (TMDB autoload via api.main _load_local_env).
$ErrorActionPreference = "Stop"
$searchDir = Split-Path $PSScriptRoot -Parent
$envFile = Join-Path $searchDir ".env"
$example = Join-Path $searchDir ".env.example"
if (Test-Path $envFile) {
    exit 0
}
if (-not (Test-Path $example)) {
    Write-Error "Missing .env.example in $searchDir"
}
Copy-Item $example $envFile
Write-Host "Created $envFile - set TMDB_API_KEY (themoviedb.org) or rely on operator_defaults.py"
