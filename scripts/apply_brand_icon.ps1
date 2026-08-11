# Generates all Android launcher / logo assets from a single source PNG.
#
# Source: app/src/main/res/raw/brand_icon.png (must be a square PNG, ideally >= 1024 x 1024).
# Output:
#   - drawable/logo.png, logo_in_app.png                    (referenced by AndroidManifest + UI)
#   - mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png + ic_launcher_round.png
#       (old .webp variants are deleted so the resource system picks the new PNGs)
#
# Launcher icons are composited onto a solid teal square so the source PNG's soft
# drop-shadow edges do not bleed through and look like a whitish halo on the
# launcher background. The adaptive-icon foreground XML stacks the same teal under
# the logo bitmap, so the launcher mask (circle / squircle / square) always clips
# clean teal.
#
# logo_splash.png is intentionally NOT touched — that file is the Mehrgo branded
# splash artwork and was reverted to the original by the user.

[CmdletBinding()]
param(
    [string]$Source = "$PSScriptRoot\..\app\src\main\res\raw\brand_icon.png",
    [string]$BrandTealHex = "#1FB8B5"
)

Add-Type -AssemblyName System.Drawing

if (-not (Test-Path $Source)) {
    Write-Error "Source icon not found at: $Source"
    exit 1
}

$resRoot = Join-Path $PSScriptRoot "..\app\src\main\res"

$launcherDensities = @{
    "mipmap-mdpi"    = 48
    "mipmap-hdpi"    = 72
    "mipmap-xhdpi"   = 96
    "mipmap-xxhdpi"  = 144
    "mipmap-xxxhdpi" = 192
}

# In-drawable logos referenced at runtime. 512px is plenty for splash / in-app brand marks.
$drawableLogos = @{
    "logo.png"        = 512
    "logo_in_app.png" = 512
}

# Cache decoded source once — FromFile would lock the file, and the launcher loop
# opens it many times.
$bytes  = [System.IO.File]::ReadAllBytes((Resolve-Path $Source))
$stream = New-Object System.IO.MemoryStream (,$bytes)
$srcImage = [System.Drawing.Image]::FromStream($stream)

$brandColor = [System.Drawing.ColorTranslator]::FromHtml($BrandTealHex)

function Save-Resized {
    param(
        [string]$DestPath,
        [int]$Size,
        [bool]$FillBackground = $false
    )

    $bmp = New-Object System.Drawing.Bitmap $Size, $Size
    $bmp.SetResolution(72, 72)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality

    if ($FillBackground) {
        # Solid teal under the icon so the source PNG's transparent / shadowed
        # corners are replaced — no more whitish halo when the launcher renders it.
        $brush = New-Object System.Drawing.SolidBrush $brandColor
        $g.FillRectangle($brush, 0, 0, $Size, $Size)
        $brush.Dispose()
    }

    # Stretch the brand artwork to fully fill the canvas. Source is 878×904
    # (≈3% taller than wide) — preserving aspect ratio leaves a thin band of
    # my fill colour at the sides, which reads as a lighter "halo" because the
    # fill teal isn't a pixel-perfect match for the artwork teal. A 3% stretch
    # is imperceptible and eliminates the halo entirely.
    $g.DrawImage($srcImage, (New-Object System.Drawing.Rectangle 0, 0, $Size, $Size))
    $g.Dispose()

    $bmp.Save($DestPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "  -> $DestPath ($Size x $Size)"
}

Write-Host "Source: $Source"
Write-Host "Generating launcher icons (teal-backed)..."
foreach ($entry in $launcherDensities.GetEnumerator()) {
    $dir = Join-Path $resRoot $entry.Key
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir | Out-Null }

    foreach ($file in @("ic_launcher.png", "ic_launcher_round.png")) {
        $dest = Join-Path $dir $file
        Save-Resized -DestPath $dest -Size $entry.Value -FillBackground $true
    }

    foreach ($legacy in @("ic_launcher.webp", "ic_launcher_round.webp")) {
        $legacyPath = Join-Path $dir $legacy
        if (Test-Path $legacyPath) {
            Remove-Item $legacyPath -Force
            Write-Host "  removed legacy $legacyPath"
        }
    }
}

Write-Host ""
Write-Host "Generating drawable logos..."
$drawableDir = Join-Path $resRoot "drawable"
foreach ($entry in $drawableLogos.GetEnumerator()) {
    $dest = Join-Path $drawableDir $entry.Key
    # logo.png is also referenced by the adaptive-icon foreground XML, where the
    # XML lays solid teal under the bitmap. Leaving logo.png un-filled keeps the
    # rounded-square look intact for splash / in-app uses.
    Save-Resized -DestPath $dest -Size $entry.Value -FillBackground $false
}

$srcImage.Dispose()
$stream.Dispose()

Write-Host ""
Write-Host "Brand icon applied. Build the app to see changes."
