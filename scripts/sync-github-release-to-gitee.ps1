<#
Mirrors one public GitHub Release to the Gitee update repository.

The script deliberately runs on the publisher's computer: GitHub Actions only builds
and publishes to GitHub, while the domestic Gitee upload is made from this computer.
#>
[CmdletBinding()]
param(
    [string]$Tag,
    [string]$GitHubOwner = 'sbw-better',
    [string]$GitHubRepository = 'HuangguoPlayerAndroid',
    [string]$GiteeOwner = 'sibingwei',
    [string]$GiteeRepository = 'huangguoplayer-update',
    [string]$Token = $env:GITEE_TOKEN,
    [switch]$KeepDownloadedFiles
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

function Get-AssetUrl {
    param([object[]]$Assets, [string]$Name)
    $asset = @($Assets | Where-Object { $_.name -eq $Name }) | Select-Object -First 1
    if ($null -eq $asset -or [string]::IsNullOrWhiteSpace($asset.browser_download_url)) {
        throw "GitHub Release does not contain $Name."
    }
    return [string]$asset.browser_download_url
}

function Invoke-CurlJson {
    param([string[]]$Arguments, [string]$Operation)
    $response = & curl.exe @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Operation failed (curl exit code $LASTEXITCODE)."
    }
    try {
        return ($response -join "`n") | ConvertFrom-Json
    } catch {
        throw "$Operation returned invalid JSON."
    }
}

function Get-GiteeRelease {
    param([string]$Api, [string]$ReleaseTag, [string]$AccessToken)
    try {
        $result = Invoke-RestMethod -Method Get `
            -Uri "$Api/tags/$([uri]::EscapeDataString($ReleaseTag))" `
            -Headers @{ Authorization = "Bearer $AccessToken"; 'User-Agent' = 'HuangguoPlayer-Gitee-Sync' }
        return $result
    } catch {
        $response = $_.Exception.Response
        if ($null -ne $response -and [int]$response.StatusCode -eq 404) {
            return $null
        }
        throw
    }
}

if ([string]::IsNullOrWhiteSpace($Token)) {
    $secureToken = Read-Host 'Enter Gitee token (input is hidden)' -AsSecureString
    $Token = [System.Net.NetworkCredential]::new('', $secureToken).Password
}
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'A Gitee token is required. Set GITEE_TOKEN and open a new PowerShell window.'
}
if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    throw 'curl.exe was not found. It is included with supported Windows versions.'
}

$githubHeaders = @{ 'User-Agent' = 'HuangguoPlayer-Gitee-Sync'; Accept = 'application/vnd.github+json' }
$githubApi = "https://api.github.com/repos/$GitHubOwner/$GitHubRepository/releases"
if ([string]::IsNullOrWhiteSpace($Tag)) {
    Write-Host 'Reading the latest GitHub Release...'
    $githubRelease = Invoke-RestMethod -Method Get -Uri "$githubApi/latest" -Headers $githubHeaders
} else {
    Write-Host "Reading GitHub Release $Tag..."
    $githubRelease = Invoke-RestMethod -Method Get `
        -Uri "$githubApi/tags/$([uri]::EscapeDataString($Tag))" -Headers $githubHeaders
}

$releaseTag = [string]$githubRelease.tag_name
if ([string]::IsNullOrWhiteSpace($releaseTag)) {
    throw 'GitHub did not return a release tag.'
}
$assetNames = @('app-release.apk', 'app-release.apk.sha256', 'update.json')
$assetUrls = @{}
foreach ($assetName in $assetNames) {
    $assetUrls[$assetName] = Get-AssetUrl -Assets @($githubRelease.assets) -Name $assetName
}

$tempRoot = [IO.Path]::GetTempPath()
$downloadDirectory = Join-Path $tempRoot ("huangguoplayer-release-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $downloadDirectory | Out-Null

try {
    foreach ($assetName in $assetNames) {
        $destination = Join-Path $downloadDirectory $assetName
        Write-Host "Downloading $assetName..."
        Invoke-WebRequest -Uri $assetUrls[$assetName] -Headers $githubHeaders -OutFile $destination
        if (-not (Test-Path -LiteralPath $destination) -or (Get-Item -LiteralPath $destination).Length -eq 0) {
            throw "Downloaded file is missing or empty: $assetName"
        }
    }

    $metadataPath = Join-Path $downloadDirectory 'update.json'
    $metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
    if ([string]::IsNullOrWhiteSpace($metadata.apkName) -or [int64]$metadata.versionCode -le 0) {
        throw 'update.json is missing apkName or versionCode.'
    }
    if ($metadata.apkName -ne 'app-release.apk') {
        throw "This script only publishes app-release.apk; update.json requested $($metadata.apkName)."
    }
    $actualSha256 = (Get-FileHash -LiteralPath (Join-Path $downloadDirectory 'app-release.apk') -Algorithm SHA256).Hash.ToLowerInvariant()
    if ([string]::IsNullOrWhiteSpace($metadata.sha256) -or $actualSha256 -ne $metadata.sha256.ToLowerInvariant()) {
        throw 'APK SHA-256 does not match update.json; publication was cancelled.'
    }

    $giteeApi = "https://gitee.com/api/v5/repos/$GiteeOwner/$GiteeRepository/releases"
    $giteeRelease = Get-GiteeRelease -Api $giteeApi -ReleaseTag $releaseTag -AccessToken $Token
    if ($null -eq $giteeRelease) {
        Write-Host "Creating Gitee Release $releaseTag..."
        $giteeRelease = Invoke-CurlJson -Operation 'Create Gitee Release' -Arguments @(
            '--fail-with-body', '--silent', '--show-error', '--request', 'POST', $giteeApi,
            '--header', "Authorization: Bearer $Token",
            '--form', "access_token=$Token",
            '--form', "tag_name=$releaseTag",
            '--form', "name=HuangguoPlayer $releaseTag",
            '--form', "body=同步自 GitHub Release。 versionCode: $($metadata.versionCode)",
            '--form', 'target_commitish=master'
        )
    } else {
        Write-Host "Reusing existing Gitee Release $releaseTag."
    }
    if ($null -eq $giteeRelease.id -or [int64]$giteeRelease.id -le 0) {
        throw 'Gitee did not return a valid Release ID.'
    }

    $existingAssetNames = @()
    if ($null -ne $giteeRelease.assets) { $existingAssetNames += @($giteeRelease.assets | ForEach-Object { $_.name }) }
    if ($null -ne $giteeRelease.attach_files) { $existingAssetNames += @($giteeRelease.attach_files | ForEach-Object { $_.name }) }
    foreach ($assetName in $assetNames) {
        if ($existingAssetNames -contains $assetName) {
            Write-Host "Skipping existing Gitee asset: $assetName"
            continue
        }
        Write-Host "Uploading $assetName to Gitee..."
        $filePath = Join-Path $downloadDirectory $assetName
        $null = Invoke-CurlJson -Operation "Upload $assetName" -Arguments @(
            '--fail-with-body', '--silent', '--show-error', '--connect-timeout', '20', '--max-time', '900',
            '--request', 'POST', "$giteeApi/$($giteeRelease.id)/attach_files",
            '--header', "Authorization: Bearer $Token",
            '--form', "access_token=$Token",
            '--form', "owner=$GiteeOwner",
            '--form', "repo=$GiteeRepository",
            '--form', "release_id=$($giteeRelease.id)",
            '--form', "file=@$filePath"
        )
    }

    $verifiedRelease = Get-GiteeRelease -Api $giteeApi -ReleaseTag $releaseTag -AccessToken $Token
    $verifiedNames = @()
    if ($null -ne $verifiedRelease.assets) { $verifiedNames += @($verifiedRelease.assets | ForEach-Object { $_.name }) }
    if ($null -ne $verifiedRelease.attach_files) { $verifiedNames += @($verifiedRelease.attach_files | ForEach-Object { $_.name }) }
    $missing = @($assetNames | Where-Object { $verifiedNames -notcontains $_ })
    if ($missing.Count -gt 0) {
        throw "Gitee Release verification failed; missing: $($missing -join ', ')"
    }
    Write-Host "Completed: https://gitee.com/$GiteeOwner/$GiteeRepository/releases/tag/$releaseTag" -ForegroundColor Green
} finally {
    if ($KeepDownloadedFiles) {
        Write-Host "Downloaded files retained at: $downloadDirectory"
    } elseif (Test-Path -LiteralPath $downloadDirectory) {
        $resolvedDownloadDirectory = (Resolve-Path -LiteralPath $downloadDirectory).Path
        if ($resolvedDownloadDirectory.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedDownloadDirectory -Recurse -Force
        }
    }
}
