param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resolvedPath = (Resolve-Path -LiteralPath $Path).Path
$entries = [ordered]@{}

foreach ($rawLine in Get-Content -LiteralPath $resolvedPath -Encoding UTF8) {
    $trimmedLine = $rawLine.Trim()
    if (-not $trimmedLine -or $trimmedLine.StartsWith('#')) {
        continue
    }

    $pair = $rawLine.Split('=', 2)
    $key = $pair[0].Trim()
    if ($pair.Count -ne 2 -or $key -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        throw "Invalid line in environment file: $rawLine"
    }

    if ($entries.Contains($key)) {
        throw "Environment file contains a duplicate key: $key"
    }

    $entries[$key] = $pair[1]
}

$placeholderKeys = @(
    $entries.GetEnumerator() |
        Where-Object { $_.Value -match 'CHANGE[-_]ME|your-frontend\.example\.com' } |
        ForEach-Object { $_.Key }
)

if ($placeholderKeys.Count -gt 0) {
    throw "Environment file still contains placeholders: $($placeholderKeys -join ', ')"
}

if ($entries.Contains('JWT_SECRET')) {
    throw 'JWT_SECRET is no longer accepted here; configure Nacos wifi-jwt.yml instead'
}

$requiredKeys = @(
    'SPRING_PROFILES_ACTIVE',
    'MYSQL_URL',
    'MYSQL_USERNAME',
    'MYSQL_PASSWORD',
    'DB_MIGRATION_URL',
    'DB_MIGRATION_USERNAME',
    'DB_MIGRATION_PASSWORD',
    'DB_MIGRATION_BASELINE_ON_MIGRATE',
    'NACOS_SERVER_ADDR',
    'NACOS_USERNAME',
    'NACOS_PASSWORD',
    'NACOS_NAMESPACE',
    'NACOS_GROUP',
    'NACOS_DISCOVERY_IP',
    'WIFI_GATEWAY_TOKEN',
    'WIFI_INTERNAL_TOKEN',
    'WIFI_ALLOWED_ORIGIN',
    'WIFI_ALLOWED_ORIGIN_ALT',
    'WIFI_ALLOWED_ORIGINS',
    'WIFI_ALERT_HEARTBEAT_INTERVAL_MILLIS',
    'OAUTH_ALLOWED_RETURN_ORIGIN',
    'OAUTH_ALLOWED_RETURN_ORIGIN_ALT',
    'FORWARD_HEADERS_STRATEGY',
    'WIFI_TRUST_PROXY_HEADERS',
    'REDIS_HOST',
    'REDIS_PORT',
    'REDIS_PASSWORD',
    'REDIS_DATABASE',
    'REDIS_SSL',
    'REDIS_RATE_LIMIT_ENABLED',
    'MQTT_BROKER_URL',
    'MQTT_CLIENT_ID',
    'MQTT_USERNAME',
    'MQTT_PASSWORD',
    'WIFI_COMMAND_SECRET_KEY',
    'WIFI_PAYMENT_DEFAULT_CHANNEL',
    'WIFI_PAYMENT_CALLBACK_WINDOW_SECONDS',
    'WIFI_PAYMENT_LOCAL_DEMO_SECRET',
    'WIFI_AVATAR_DIR'
)
foreach ($requiredKey in $requiredKeys) {
    if (-not $entries.Contains($requiredKey)) {
        throw "Environment file is missing required key: $requiredKey"
    }
}

$requiredNonEmptyKeys = @(
    'SPRING_PROFILES_ACTIVE',
    'MYSQL_URL',
    'MYSQL_USERNAME',
    'MYSQL_PASSWORD',
    'DB_MIGRATION_URL',
    'DB_MIGRATION_USERNAME',
    'DB_MIGRATION_PASSWORD',
    'NACOS_SERVER_ADDR',
    'NACOS_USERNAME',
    'NACOS_PASSWORD',
    'NACOS_GROUP',
    'NACOS_DISCOVERY_IP',
    'WIFI_GATEWAY_TOKEN',
    'WIFI_INTERNAL_TOKEN',
    'WIFI_ALLOWED_ORIGIN',
    'WIFI_ALLOWED_ORIGIN_ALT',
    'WIFI_ALLOWED_ORIGINS',
    'WIFI_ALERT_HEARTBEAT_INTERVAL_MILLIS',
    'OAUTH_ALLOWED_RETURN_ORIGIN',
    'OAUTH_ALLOWED_RETURN_ORIGIN_ALT',
    'FORWARD_HEADERS_STRATEGY',
    'WIFI_TRUST_PROXY_HEADERS',
    'REDIS_HOST',
    'REDIS_PORT',
    'REDIS_DATABASE',
    'REDIS_SSL',
    'REDIS_RATE_LIMIT_ENABLED',
    'MQTT_BROKER_URL',
    'MQTT_CLIENT_ID',
    'WIFI_PAYMENT_DEFAULT_CHANNEL',
    'WIFI_PAYMENT_CALLBACK_WINDOW_SECONDS',
    'WIFI_PAYMENT_LOCAL_DEMO_SECRET',
    'WIFI_AVATAR_DIR'
)
foreach ($requiredNonEmptyKey in $requiredNonEmptyKeys) {
    if ([string]::IsNullOrWhiteSpace([string]$entries[$requiredNonEmptyKey])) {
        throw "Environment file contains an empty required value: $requiredNonEmptyKey"
    }
}

if ($entries['SPRING_PROFILES_ACTIVE'] -ne 'prod') {
    throw 'Production deployment requires SPRING_PROFILES_ACTIVE=prod'
}
if ($entries['DB_MIGRATION_BASELINE_ON_MIGRATE'] -ne 'false') {
    throw 'Normal production deployment requires DB_MIGRATION_BASELINE_ON_MIGRATE=false'
}
if ($entries['REDIS_RATE_LIMIT_ENABLED'] -ne 'true') {
    throw 'Production deployment requires REDIS_RATE_LIMIT_ENABLED=true'
}

$gatewayToken = [string]$entries['WIFI_GATEWAY_TOKEN']
$internalToken = [string]$entries['WIFI_INTERNAL_TOKEN']

if ($gatewayToken.Length -lt 16 -or $internalToken.Length -lt 16) {
    throw 'WIFI_GATEWAY_TOKEN and WIFI_INTERNAL_TOKEN must contain at least 16 characters'
}
if ($gatewayToken -eq $internalToken) {
    throw 'WIFI_GATEWAY_TOKEN and WIFI_INTERNAL_TOKEN must use different values'
}
if ($gatewayToken -ne $gatewayToken.Trim() -or $internalToken -ne $internalToken.Trim()) {
    throw 'WIFI_GATEWAY_TOKEN and WIFI_INTERNAL_TOKEN must not contain leading or trailing whitespace'
}
$monitorOrigins = @(([string]$entries['WIFI_ALLOWED_ORIGINS']).Split(',') | ForEach-Object { $_.Trim() })
if ($entries['WIFI_ALLOWED_ORIGIN'] -eq '*' -or
        $entries['WIFI_ALLOWED_ORIGIN_ALT'] -eq '*' -or
        $monitorOrigins -contains '*') {
    throw 'Production browser Origin configuration must not contain *'
}

foreach ($entry in $entries.GetEnumerator()) {
    [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
}

Write-Host "Loaded $($entries.Count) environment variables from $resolvedPath into the current PowerShell process."
