param(
    [string]$ServerAddress = $env:NACOS_SERVER_ADDR,
    [string]$Username = $env:NACOS_USERNAME,
    [string]$Password = $env:NACOS_PASSWORD,
    [string]$Namespace = $env:NACOS_NAMESPACE,
    [string]$Group = $env:NACOS_GROUP,
    [string]$DataId = 'wifi-jwt.yml'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

foreach ($requiredValue in @{
        ServerAddress = $ServerAddress
        Username = $Username
        Password = $Password
        Group = $Group
        DataId = $DataId
    }.GetEnumerator()) {
    if ([string]::IsNullOrWhiteSpace([string]$requiredValue.Value)) {
        throw "Missing required Nacos verification value: $($requiredValue.Key)"
    }
}

$serverAddresses = @(
    $ServerAddress.Split(',') |
        ForEach-Object { $_.Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)
if ($serverAddresses.Count -eq 0) {
    throw 'NACOS_SERVER_ADDR does not contain a usable server address.'
}

$configQuery = 'dataId={0}&group={1}' -f
    [Uri]::EscapeDataString($DataId),
    [Uri]::EscapeDataString($Group)
if (-not [string]::IsNullOrWhiteSpace($Namespace)) {
    $configQuery += '&tenant=' + [Uri]::EscapeDataString($Namespace)
}

$verificationResults = @()
foreach ($server in $serverAddresses) {
    $baseUri = $server.TrimEnd('/')
    if ($baseUri -notmatch '^https?://') {
        $baseUri = "http://$baseUri"
    }
    if ($baseUri -notmatch '/nacos$') {
        $baseUri = "$baseUri/nacos"
    }
    $configUri = "$baseUri/v1/cs/configs?$configQuery"

    $anonymousStatus = $null
    try {
        $anonymousResponse = Invoke-WebRequest -UseBasicParsing -Uri $configUri -Method Get -TimeoutSec 10
        $anonymousStatus = [int]$anonymousResponse.StatusCode
    } catch {
        if ($null -eq $_.Exception.Response) {
            throw "Unable to reach Nacos node $baseUri while checking anonymous access."
        }
        $anonymousStatus = [int]$_.Exception.Response.StatusCode
    }

    if ($anonymousStatus -eq 200) {
        throw "Nacos authentication is not protecting ${DataId}: anonymous access to $baseUri returned HTTP 200. Do not start Auth or Gateway in production."
    }
    if ($anonymousStatus -notin @(401, 403)) {
        throw "Unable to prove that Nacos authentication protects $DataId on $baseUri. Anonymous request returned HTTP $anonymousStatus; expected 401 or 403."
    }

    try {
        $loginResponse = Invoke-RestMethod `
            -Uri "$baseUri/v1/auth/users/login" `
            -Method Post `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{ username = $Username; password = $Password } `
            -TimeoutSec 10
    } catch {
        $loginStatus = if ($null -ne $_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "Nacos login failed on $baseUri with HTTP $loginStatus."
    }

    $accessTokenProperty = $loginResponse.PSObject.Properties['accessToken']
    if ($null -eq $accessTokenProperty) {
        throw "Nacos login on $baseUri succeeded without an accessToken property."
    }
    $accessToken = [string]$accessTokenProperty.Value
    if ([string]::IsNullOrWhiteSpace($accessToken)) {
        throw "Nacos login on $baseUri succeeded without returning an access token."
    }

    $authenticatedUri = $configUri + '&accessToken=' + [Uri]::EscapeDataString($accessToken)
    try {
        $configResponse = Invoke-WebRequest -UseBasicParsing -Uri $authenticatedUri -Method Get -TimeoutSec 10
    } catch {
        $configStatus = if ($null -ne $_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        throw "Authenticated access to $DataId failed on $baseUri with HTTP $configStatus."
    }

    $configBytes = [Text.Encoding]::UTF8.GetBytes([string]$configResponse.Content)
    if ($configBytes.Length -eq 0) {
        throw "Authenticated Nacos request returned an empty $DataId configuration on $baseUri."
    }

    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $configHash = ([BitConverter]::ToString($sha256.ComputeHash($configBytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }

    $verificationResults += [pscustomobject]@{
        Node = $baseUri
        Status = [int]$configResponse.StatusCode
        Bytes = $configBytes.Length
        Sha256 = $configHash
    }
}

$distinctHashes = @($verificationResults | Select-Object -ExpandProperty Sha256 -Unique)
if ($distinctHashes.Count -ne 1) {
    throw "Nacos nodes returned different $DataId content hashes. Stop deployment and reconcile the cluster configuration."
}

foreach ($result in $verificationResults) {
    Write-Host "Nacos authentication and $DataId access verified on $($result.Node) (HTTP $($result.Status), bytes $($result.Bytes), sha256 $($result.Sha256))."
}
