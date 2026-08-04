param(
    [string]$Path = (Join-Path $PSScriptRoot '..\.env')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$entries = [ordered]@{}

foreach ($rawLine in Get-Content -LiteralPath $resolvedPath -Encoding UTF8) {
    $line = $rawLine.Trim()
    if (-not $line -or $line.StartsWith('#')) {
        continue
    }

    $pair = $line.Split('=', 2)
    if ($pair.Count -ne 2 -or $pair[0] -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid line in environment file: $rawLine"
    }

    $entries[$pair[0]] = $pair[1].Trim()
}

$placeholderKeys = @(
    $entries.GetEnumerator() |
        Where-Object { $_.Value -match 'CHANGE_ME|your-frontend\.example\.com' } |
        ForEach-Object { $_.Key }
)

if ($placeholderKeys.Count -gt 0) {
    throw "Environment file still contains placeholders: $($placeholderKeys -join ', ')"
}

$requiredKeys = @(
    'SPRING_PROFILES_ACTIVE',
    'JWT_SECRET',
    'WIFI_GATEWAY_TOKEN',
    'WIFI_INTERNAL_TOKEN'
)
foreach ($requiredKey in $requiredKeys) {
    if (-not $entries.Contains($requiredKey)) {
        throw "Environment file is missing required key: $requiredKey"
    }
}

if ($entries['SPRING_PROFILES_ACTIVE'] -ne 'prod') {
    throw 'Production deployment requires SPRING_PROFILES_ACTIVE=prod'
}

$jwtSecret = [string]$entries['JWT_SECRET']
$gatewayToken = [string]$entries['WIFI_GATEWAY_TOKEN']
$internalToken = [string]$entries['WIFI_INTERNAL_TOKEN']

if ($jwtSecret.Length -lt 32) {
    throw 'JWT_SECRET must contain at least 32 characters'
}
if ($gatewayToken.Length -lt 16 -or $internalToken.Length -lt 16) {
    throw 'WIFI_GATEWAY_TOKEN and WIFI_INTERNAL_TOKEN must contain at least 16 characters'
}
if ($gatewayToken -eq $internalToken) {
    throw 'WIFI_GATEWAY_TOKEN and WIFI_INTERNAL_TOKEN must use different values'
}

foreach ($entry in $entries.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

Write-Host "Loaded $($entries.Count) environment variables from $resolvedPath into the current PowerShell process."
