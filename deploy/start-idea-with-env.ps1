param(
    [Parameter(Mandatory = $true)]
    [ValidateNotNullOrEmpty()]
    [string]$EnvPath,
    [string]$IdeaPath = $env:WIFI_IDEA_PATH,
    [string]$ProjectPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($ProjectPath)) {
    $ProjectPath = Join-Path $PSScriptRoot '..'
}

$existingIdea = @(Get-Process -Name 'idea64' -ErrorAction SilentlyContinue)
if ($existingIdea.Count -gt 0) {
    $processIds = ($existingIdea | ForEach-Object { $_.Id }) -join ', '
    throw "IntelliJ IDEA is already running (PID: $processIds). Exit every IDEA window and wait for idea64.exe to stop before loading a changed environment file."
}

if ([string]::IsNullOrWhiteSpace($IdeaPath)) {
    $ideaCommand = Get-Command 'idea64.exe' -ErrorAction SilentlyContinue
    if ($null -eq $ideaCommand) {
        throw 'IntelliJ IDEA was not found. Pass -IdeaPath or set WIFI_IDEA_PATH to the full path of idea64.exe.'
    }
    $IdeaPath = $ideaCommand.Source
}

$resolvedIdeaPath = (Resolve-Path -LiteralPath $IdeaPath).Path
$resolvedProjectPath = (Resolve-Path -LiteralPath $ProjectPath).Path
$resolvedEnvPath = (Resolve-Path -LiteralPath $EnvPath).Path

& (Join-Path $PSScriptRoot 'load-env.ps1') -Path $resolvedEnvPath
& (Join-Path $PSScriptRoot 'verify-nacos-auth.ps1')

$ideaProcess = Start-Process -FilePath $resolvedIdeaPath -ArgumentList @($resolvedProjectPath) -PassThru
Write-Host "Started IntelliJ IDEA (PID: $($ideaProcess.Id)) for $resolvedProjectPath with the validated environment file."
