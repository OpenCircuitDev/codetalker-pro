<#
.SYNOPSIS
    Rotate the rehearsal keystore (abc123) to a production keystore with a strong password.

.DESCRIPTION
    Single-command rotation workflow for the codetalker companion app's release
    signing key. Generates a 32-byte cryptographically-random password, creates a
    new PKCS12 keystore at $HOME\codetalker-release-PROD.keystore, updates
    ~/.gradle/gradle.properties to point at the new keystore, rebuilds a signed
    AAB + APK, verifies the signature, and base64-encodes the keystore for
    pasting into the CCT_KEYSTORE_BASE64 GitHub Secret.

    The new password is printed once at the end. SAVE IT TO A PASSWORD MANAGER
    IMMEDIATELY. Loss = locked out of Play Store updates forever.

.PARAMETER ProdKeystorePath
    Where to write the new keystore. Default: $HOME\codetalker-release-PROD.keystore

.PARAMETER KeepRehearsal
    If set, leaves the existing rehearsal keystore in place. Default: false (deletes it).

.EXAMPLE
    .\rotate-keystore.ps1

.EXAMPLE
    .\rotate-keystore.ps1 -ProdKeystorePath "D:\secure\codetalker.keystore" -KeepRehearsal
#>

[CmdletBinding()]
param(
    [string]$ProdKeystorePath = "$HOME\codetalker-release-PROD.keystore",
    [switch]$KeepRehearsal
)

$ErrorActionPreference = "Stop"

# --- Locate JBR keytool + gradle.properties ---
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    throw "keytool not found at $keytool. Update path or install Android Studio."
}

$gradleProps = "$HOME\.gradle\gradle.properties"
$rehearsalKeystore = "$HOME\codetalker-release.keystore"

# --- Refuse to overwrite an existing prod keystore ---
if (Test-Path $ProdKeystorePath) {
    Write-Error "Production keystore already exists at $ProdKeystorePath. Refusing to overwrite. Move/delete it first if you really want to regenerate."
    exit 1
}

# --- Generate strong password ---
Write-Host "[1/7] Generating strong 32-byte URL-safe password..." -ForegroundColor Cyan
$strongPass = python -c "import secrets; print(secrets.token_urlsafe(32))"
if (-not $strongPass) { throw "password generation failed" }

# --- Generate the keystore non-interactively ---
Write-Host "[2/7] Generating production keystore at $ProdKeystorePath..." -ForegroundColor Cyan
& $keytool -genkey -v `
    -keystore $ProdKeystorePath `
    -alias codetalker `
    -keyalg RSA -keysize 2048 -validity 25000 -storetype PKCS12 `
    -storepass $strongPass -keypass $strongPass `
    -dname "CN=codetalker companion, OU=Open Circuit Dev, O=Open Circuit Dev"
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

# --- Update gradle.properties ---
Write-Host "[3/7] Updating ~/.gradle/gradle.properties..." -ForegroundColor Cyan
$existing = if (Test-Path $gradleProps) { Get-Content $gradleProps } else { @() }
# Strip old CCT_* entries and the rehearsal-keystore comment block
$cleaned = $existing | Where-Object {
    $_ -notmatch "^CCT_KEYSTORE_(FILE|PASSWORD)=" -and
    $_ -notmatch "^CCT_KEY_(ALIAS|PASSWORD)=" -and
    $_ -notmatch "^# CCT-32 release signing"
}
$prodPathForwardSlash = $ProdKeystorePath -replace '\\', '/'
$newLines = @(
    "",
    "# CCT-32 release signing — PRODUCTION keystore (rotated $(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssZ'))",
    "CCT_KEYSTORE_FILE=$prodPathForwardSlash",
    "CCT_KEYSTORE_PASSWORD=$strongPass",
    "CCT_KEY_ALIAS=codetalker",
    "CCT_KEY_PASSWORD=$strongPass"
)
($cleaned + $newLines) | Set-Content $gradleProps -Encoding UTF8

# --- Rebuild signed AAB + APK ---
Write-Host "[4/7] Building signed AAB + APK with new keystore..." -ForegroundColor Cyan
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
Push-Location "$PSScriptRoot\.."
try {
    & .\gradlew.bat bundleRelease assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "gradle build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

# --- Verify signature ---
Write-Host "[5/7] Verifying APK signature..." -ForegroundColor Cyan
$apksigner = (Get-ChildItem "$HOME\AppData\Local\Android\Sdk\build-tools" -Directory |
              Sort-Object Name -Descending |
              Select-Object -First 1).FullName + "\apksigner.bat"
$apkPath = "$PSScriptRoot\..\app\build\outputs\apk\release\app-release.apk"
& $apksigner verify --verbose --print-certs $apkPath
if ($LASTEXITCODE -ne 0) { throw "apksigner verify failed" }

# --- Base64-encode keystore for GitHub Secret ---
Write-Host "[6/7] Encoding keystore as base64 for CCT_KEYSTORE_BASE64 Secret..." -ForegroundColor Cyan
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ProdKeystorePath))
$b64Path = "$ProdKeystorePath.b64"
$b64 | Set-Content $b64Path -Encoding ASCII -NoNewline

# --- Optional cleanup of rehearsal keystore ---
if (-not $KeepRehearsal -and (Test-Path $rehearsalKeystore)) {
    Write-Host "[7/7] Removing rehearsal keystore at $rehearsalKeystore..." -ForegroundColor Cyan
    Remove-Item $rehearsalKeystore
}

# --- Final summary ---
Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host " Rotation complete." -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host " Production keystore: $ProdKeystorePath"
Write-Host " Base64 (for GitHub Secret CCT_KEYSTORE_BASE64): $b64Path"
Write-Host ""
Write-Host " *** PASSWORD (save to 1Password / Bitwarden NOW) ***" -ForegroundColor Yellow
Write-Host " $strongPass" -ForegroundColor Yellow
Write-Host ""
Write-Host " Signed artifacts:"
Write-Host "   AAB: app/build/outputs/bundle/release/app-release.aab"
Write-Host "   APK: app/build/outputs/apk/release/app-release.apk"
Write-Host ""
Write-Host " Next steps (paste these into your shell after saving the password):"
Write-Host ""
Write-Host "   gh secret set CCT_KEYSTORE_BASE64 -R OpenCircuitDev/codetalker-pro < `"$b64Path`""
Write-Host "   gh secret set CCT_KEYSTORE_PASSWORD -b `"$strongPass`" -R OpenCircuitDev/codetalker-pro"
Write-Host "   gh secret set CCT_KEY_ALIAS -b 'codetalker' -R OpenCircuitDev/codetalker-pro"
Write-Host "   gh secret set CCT_KEY_PASSWORD -b `"$strongPass`" -R OpenCircuitDev/codetalker-pro"
Write-Host ""
Write-Host "   # then tag + watch CI:"
Write-Host "   git -C `"$PSScriptRoot\..`" tag v0.1.0"
Write-Host "   git -C `"$PSScriptRoot\..`" push origin v0.1.0"
Write-Host "   gh run watch -R OpenCircuitDev/codetalker-pro"
Write-Host ""
Write-Host " *** DELETE $b64Path AFTER setting the GitHub Secret ***" -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Green
