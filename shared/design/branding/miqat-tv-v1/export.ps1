param([string]$Master, [string]$Icon, [string]$Splash)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$output = $PSScriptRoot
function Export-Png([string]$Source, [string]$Name, [int]$Width, [int]$Height) {
    $sourceImage = [System.Drawing.Image]::FromFile($Source)
    $bitmap = New-Object System.Drawing.Bitmap($Width, $Height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($sourceImage, 0, 0, $Width, $Height)
        $bitmap.Save((Join-Path $output $Name), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose(); $sourceImage.Dispose() }
    $check = [System.Drawing.Image]::FromFile((Join-Path $output $Name))
    try { if ($check.Width -ne $Width -or $check.Height -ne $Height) { throw "Wrong export dimensions: $Name" } }
    finally { $check.Dispose() }
}
foreach ($entry in @(@($Master, 'miqat-tv-logo-master.png'), @($Icon, 'miqat-tv-icon-source.png'), @($Splash, 'miqat-tv-splash-source.png'))) {
    $destination = Join-Path $output $entry[1]
    if ([System.IO.Path]::GetFullPath($entry[0]) -ne [System.IO.Path]::GetFullPath($destination)) {
        Copy-Item -LiteralPath $entry[0] -Destination $destination
    }
}
foreach ($size in @(1024, 512, 256, 130, 80, 48, 32)) {
    Export-Png $Icon "miqat-tv-icon-$size.png" $size $size
}
Export-Png $Splash 'miqat-tv-splash-1920x1080.png' 1920 1080
$preview = New-Object System.Drawing.Bitmap(1000, 370)
$canvas = [System.Drawing.Graphics]::FromImage($preview)
$font = New-Object System.Drawing.Font('Segoe UI', 13)
try {
    $canvas.Clear([System.Drawing.Color]::FromArgb(239, 231, 214))
    $left = 30
    foreach ($size in @(32, 48, 80, 130, 256)) {
        $image = [System.Drawing.Image]::FromFile((Join-Path $output "miqat-tv-icon-$size.png"))
        try { $canvas.DrawImageUnscaled($image, $left, 35); $canvas.DrawString("$size px", $font, [System.Drawing.Brushes]::Black, $left, 310) }
        finally { $image.Dispose() }
        $left += $size + 65
    }
    $preview.Save((Join-Path $output 'icon-size-review.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $canvas.Dispose(); $preview.Dispose() }
$files = Get-ChildItem $output -Filter '*.png' | ForEach-Object {
    $image = [System.Drawing.Image]::FromFile($_.FullName)
    try { [ordered]@{ file = $_.Name; width = $image.Width; height = $image.Height; bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash } }
    finally { $image.Dispose() }
}
[ordered]@{
    brand = 'Miqat'; arabicShortName = 'ميقات'; date = '2026-11-17'
    status = 'Source raster pack integrated by site/scripts/splash.mjs; see docs/MIQAT-REBRAND.md for validation'
    generation = 'Built-in ImageGen, edited from the owner-selected calligraphic logo; deterministic PNG size exports via System.Drawing'
    design = 'Selected scalloped mihrab; Arabic primary; English at broken base; separate centered TV. Icon omits English and TV for clarity.'
    promptSummary = 'Preserve selected lettering and scalloped arch; smooth contours; strengthen frame; gold on petrol. Icon uses Arabic-only emblem with closed base. Splash uses full centered bilingual lockup.'
    limits = @('Raster artwork, not vector paths', 'Generated sources retain slight tonal variation', '32 px is a recognition mark; full calligraphic detail requires larger sizes', 'Physical-device and adaptive-mask acceptance remain pending; see docs/MIQAT-REBRAND.md', 'Installed package IDs and saved preferences must remain stable')
    files = @($files)
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'manifest.json') -Encoding utf8
Write-Output "Verified $($files.Count) PNG files in $output"
