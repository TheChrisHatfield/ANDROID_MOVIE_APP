# Start search API and run host-side smoke (requires py -3.11 + services/search installed)
$ErrorActionPreference = "Stop"
$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$searchDir = Join-Path $repoRoot "services\search"

Push-Location $searchDir
py -3.11 -m pip install -e ".[dev]" -q
$api = Start-Process -FilePath "py" -ArgumentList "-3.11","-m","uvicorn","api.main:app","--host","127.0.0.1","--port","8765" -WorkingDirectory $searchDir -PassThru -WindowStyle Hidden
Pop-Location

$ready = $false
for ($i = 0; $i -lt 30; $i++) {
    try {
        $r = Invoke-WebRequest -Uri "http://127.0.0.1:8765/v1/health" -UseBasicParsing -TimeoutSec 2
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch {}
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    Write-Error "Search API did not start on :8765"
    Stop-Process -Id $api.Id -Force -ErrorAction SilentlyContinue
    exit 1
}
try {
    py -3.11 (Join-Path $PSScriptRoot "smoke_search_api.py")
    $code = $LASTEXITCODE
} finally {
    Stop-Process -Id $api.Id -Force -ErrorAction SilentlyContinue
}
exit $code
