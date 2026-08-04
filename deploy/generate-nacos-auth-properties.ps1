param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd('\', '/')
$resolvedOutputPath = [IO.Path]::GetFullPath($OutputPath)
$repositoryPrefix = $repositoryRoot + [IO.Path]::DirectorySeparatorChar
if ($resolvedOutputPath.Equals($repositoryRoot, [StringComparison]::OrdinalIgnoreCase) -or
        $resolvedOutputPath.StartsWith($repositoryPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Nacos authentication secrets must be written outside the Git repository.'
}

$outputDirectory = [IO.Path]::GetDirectoryName($resolvedOutputPath)
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    throw "Output directory does not exist: $outputDirectory"
}
if ((Test-Path -LiteralPath $resolvedOutputPath) -and -not $Force) {
    throw 'Output file already exists. Use -Force only when intentionally rotating all Nacos nodes together.'
}

$random = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    function New-RandomBytes([int]$Length) {
        $bytes = New-Object byte[] $Length
        $random.GetBytes($bytes)
        return ,$bytes
    }

    $identityKeyBytes = New-RandomBytes 16
    $identityKey = 'Wifi-Nacos-' + ([BitConverter]::ToString($identityKeyBytes)).Replace('-', '')
    $identityValue = [Convert]::ToBase64String((New-RandomBytes 32))
    $tokenSecret = [Convert]::ToBase64String((New-RandomBytes 32))
} finally {
    $random.Dispose()
}

$properties = @(
    'nacos.core.auth.system.type=nacos',
    'nacos.core.auth.enabled=true',
    'nacos.core.auth.enable.userAgentAuthWhite=false',
    "nacos.core.auth.server.identity.key=$identityKey",
    "nacos.core.auth.server.identity.value=$identityValue",
    "nacos.core.auth.plugin.nacos.token.secret.key=$tokenSecret"
)

$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($resolvedOutputPath, $properties, $utf8WithoutBom)

Write-Host "Generated a Nacos authentication properties snippet at $resolvedOutputPath. Restrict its filesystem permissions and apply the same values to every node."
