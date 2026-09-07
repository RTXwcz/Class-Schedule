$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Copy-Item -LiteralPath (Join-Path $projectRoot '课表.html') -Destination (Join-Path $projectRoot 'apk/www/index.html')
Copy-Item -LiteralPath (Join-Path $projectRoot 'schedule-contract.js') -Destination (Join-Path $projectRoot 'apk/www/schedule-contract.js')
Write-Output 'Web HTML and shared contract synchronized.'
