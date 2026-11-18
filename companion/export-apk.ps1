$ErrorActionPreference = 'Stop'
$output = Join-Path $PSScriptRoot 'android/build/outputs/apk/debug'
$metadata = Get-Content -LiteralPath (Join-Path $output 'output-metadata.json') -Raw | ConvertFrom-Json
if ($metadata.applicationId -ne 'tv.miqat.companion' -or $metadata.elements.Count -ne 1) {
    throw 'Build the Miqat companion debug APK before exporting.'
}
$artifact = $metadata.elements[0]
$source = Join-Path $output $artifact.outputFile
$commonGit = & git -C $PSScriptRoot rev-parse --git-common-dir
if ($LASTEXITCODE -ne 0) { throw 'Cannot locate the shared repository build folder.' }
if (-not [IO.Path]::IsPathRooted($commonGit)) { $commonGit = Join-Path $PSScriptRoot $commonGit }
$repository = Split-Path -Parent (Resolve-Path -LiteralPath $commonGit).Path
$destination = Join-Path $repository 'state/builds/companion'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash
foreach ($name in @("Miqat-$($artifact.versionName)-debug.apk", 'Miqat-latest.apk')) {
    $target = Join-Path $destination $name
    Copy-Item -LiteralPath $source -Destination $target -Force
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $sourceHash) {
        throw "APK export verification failed: $target"
    }
}
Write-Output (Join-Path $destination 'Miqat-latest.apk')
