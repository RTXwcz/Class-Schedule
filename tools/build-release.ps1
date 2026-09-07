param(
    [Parameter(Mandatory)][string]$Keystore,
    [Parameter(Mandatory)][string]$CredentialFile,
    [string]$KeyAlias = 'class-schedule'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$credential = Import-Clixml -LiteralPath $CredentialFile
try {
    $env:CLASS_SCHEDULE_KEYSTORE = (Resolve-Path -LiteralPath $Keystore).Path
    $env:CLASS_SCHEDULE_KEY_ALIAS = $KeyAlias
    $env:CLASS_SCHEDULE_STORE_PASSWORD = $credential.GetNetworkCredential().Password
    $env:CLASS_SCHEDULE_KEY_PASSWORD = $env:CLASS_SCHEDULE_STORE_PASSWORD
    Push-Location (Join-Path $projectRoot 'apk/android')
    try {
        & .\gradlew.bat :app:assembleRelease :app:lintRelease --no-daemon --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    } finally { Pop-Location }
} finally {
    Remove-Item Env:CLASS_SCHEDULE_KEYSTORE, Env:CLASS_SCHEDULE_KEY_ALIAS, Env:CLASS_SCHEDULE_STORE_PASSWORD, Env:CLASS_SCHEDULE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
