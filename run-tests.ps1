<#
.SYNOPSIS
    Run the Video Splitter test suite (unit + instrumented + monkey + report).

.DESCRIPTION
    One-shot test runner. Verifies device, builds debug + androidTest APKs,
    runs JVM unit tests, runs instrumented tests, optionally fires a monkey
    smoke test, pulls reports + screenshots into logs/test-reports/<ts>/,
    and prints a colour-coded pass/fail summary. Exit code is non-zero
    on any red.

    See TESTING-AGENT.md for the full playbook.

.PARAMETER SkipUnit
    Skip JVM unit tests (src/test/).

.PARAMETER SkipInstrumented
    Skip instrumented tests (src/androidTest/).

.PARAMETER OnlyClass
    Run only the given fully-qualified test class for instrumented tests.

.PARAMETER OnlyMethod
    Run only "package.Class#method" for instrumented tests.

.PARAMETER Monkey
    Fire a monkey smoke test for N events after instrumented tests.

.PARAMETER Clean
    Uninstall the app and test packages before running. Use sparingly —
    deletes any saved SAF folder grants.

.PARAMETER OpenReport
    Open the HTML test report in the default browser when done.

.EXAMPLE
    .\run-tests.ps1
    Runs unit + instrumented and prints summary.

.EXAMPLE
    .\run-tests.ps1 -OnlyClass com.splitandmerge.mkvslice.ui.SplitConfigScreenTest -OpenReport

.EXAMPLE
    .\run-tests.ps1 -Monkey 2000 -OpenReport
#>

[CmdletBinding()]
param(
    [switch]$SkipUnit,
    [switch]$SkipInstrumented,
    [string]$OnlyClass,
    [string]$OnlyMethod,
    [int]$Monkey = 0,
    [switch]$Clean,
    [switch]$OpenReport
)

$ErrorActionPreference = 'Stop'

# ---------- Constants ----------
$Script:Adb        = 'D:\idm\platform-tools-latest-windows\platform-tools\adb.exe'
$Script:Gradle     = '.\gradlew.bat'
$Script:AppPkg     = 'com.splitandmerge.mkvslice.debug'
$Script:TestPkg    = 'com.splitandmerge.mkvslice.debug.test'
$Script:ApkDebug   = 'app\build\outputs\apk\debug\app-debug.apk'
$Script:ApkAndroidTest = 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'

# ---------- Helpers ----------
function Write-Section([string]$msg) {
    Write-Host ''
    Write-Host ('=' * 60) -ForegroundColor DarkCyan
    Write-Host (" $msg") -ForegroundColor Cyan
    Write-Host ('=' * 60) -ForegroundColor DarkCyan
}

function Write-Ok([string]$msg)   { Write-Host "  [OK] $msg" -ForegroundColor Green }
function Write-Bad([string]$msg)  { Write-Host "  [FAIL] $msg" -ForegroundColor Red }
function Write-Info([string]$msg) { Write-Host "  [INFO] $msg" -ForegroundColor Gray }

function Assert-File([string]$path, [string]$desc) {
    if (-not (Test-Path $path)) {
        Write-Bad "Missing: $desc ($path)"
        throw "Required file not found: $path"
    }
    Write-Ok "$desc found"
}

function Get-OneDevice {
    $output = & $Script:Adb devices 2>&1
    $lines  = $output -split "`n" | Where-Object {
        $_ -match '\sdevice$' -and $_ -notmatch 'List of'
    }
    if ($lines.Count -eq 0) {
        throw "No devices online. Connect a device, allow USB debugging, then re-run."
    }
    if ($lines.Count -gt 1) {
        Write-Bad "More than one device connected:"
        $lines | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
        throw "Multiple devices. Pass -s SERIAL via ADB env, or disconnect extras."
    }
    $serial = ($lines[0] -split '\s+')[0]
    Write-Ok "Device: $serial"
    return $serial
}

function Invoke-GradleStep {
    param(
        [string[]]$GradleArgs,
        [string]$LogPath,
        [string]$Label
    )
    Write-Info "Running: $Script:Gradle $($GradleArgs -join ' ')"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $Script:Gradle @GradleArgs *>&1
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output | Tee-Object -FilePath $LogPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Bad "$Label FAILED (exit $LASTEXITCODE)  Log: $LogPath"
        return $false
    }
    Write-Ok "$Label OK"
    return $true
}

function Get-TestSummary([string]$reportRoot) {
    $idx = Join-Path $reportRoot 'index.html'
    if (-not (Test-Path $idx)) { return @{ passed=0; failed=0; skipped=0; ok=$false } }
    $html = Get-Content $idx -Raw
    $passed  = if ($html -match 'class="counter">(\d+)</div>\s*<p>tests'  ) { [int]$matches[1] } else { 0 }
    $failed  = if ($html -match 'class="counter">(\d+)</div>\s*<p>failures') { [int]$matches[1] } else { 0 }
    $skipped = if ($html -match 'class="counter">(\d+)</div>\s*<p>ignored' ) { [int]$matches[1] } else { 0 }
    return @{ passed=$passed; failed=$failed; skipped=$skipped; ok=$true }
}

# ---------- Pre-flight ----------
Write-Section "PRE-FLIGHT"
Assert-File $Script:Gradle 'Gradle wrapper'
Assert-File $Script:Adb    'ADB executable'

if ($Clean) {
    Write-Info 'Clean: uninstalling test + app packages…'
    & $Script:Adb uninstall $Script:TestPkg 2>&1 | Out-Null
    & $Script:Adb uninstall $Script:AppPkg  2>&1 | Out-Null
    Write-Ok 'Clean done.'
}

if (-not $SkipInstrumented) {
    $serial = Get-OneDevice
}

# ---------- Output dirs ----------
$ts        = (Get-Date).ToString('yyyyMMdd_HHmmss')
$reportDir = Join-Path 'logs' "test-reports/$ts"
New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
Write-Ok "Report dir: $reportDir"

$unitLog  = Join-Path $reportDir 'unit.log'
$instLog  = Join-Path $reportDir 'instrumented.log'
$buildLog = Join-Path $reportDir 'build.log'
$monkLog  = Join-Path $reportDir 'monkey.log'

# ---------- Unit tests ----------
$unitOk = $true
if (-not $SkipUnit) {
    Write-Section "UNIT TESTS"
    $unitOk = Invoke-GradleStep `
        -GradleArgs @(':app:testDebugUnitTest','--no-daemon','--warning-mode=summary') `
        -LogPath $unitLog `
        -Label 'Unit tests'

    # Copy report
    $unitReport = 'app\build\reports\tests\testDebugUnitTest'
    if (Test-Path $unitReport) {
        Copy-Item $unitReport (Join-Path $reportDir 'unit-html') -Recurse -Force
    }
} else {
    Write-Section "UNIT TESTS"
    Write-Info 'Skipped (--SkipUnit).'
}

# ---------- Build APKs ----------
$buildOk = $true
if (-not $SkipInstrumented) {
    Write-Section "BUILD DEBUG + ANDROIDTEST APKs"
    $buildOk = Invoke-GradleStep `
        -GradleArgs @(':app:assembleDebug',':app:assembleDebugAndroidTest','--no-daemon','--warning-mode=summary') `
        -LogPath $buildLog `
        -Label 'Assemble debug + androidTest'
}

# ---------- Instrumented tests ----------
$instOk = $true
$instSummary = @{ passed=0; failed=0; skipped=0; ok=$false }
if (-not $SkipInstrumented -and $buildOk) {
    Write-Section "INSTRUMENTED TESTS"

    $args = @(':app:connectedDebugAndroidTest','--no-daemon')
    if ($OnlyClass)  { $args += "-Pandroid.testInstrumentationRunnerArguments.class=$OnlyClass" }
    if ($OnlyMethod) { $args += "-Pandroid.testInstrumentationRunnerArguments.class=$OnlyMethod" }

    $instOk = Invoke-GradleStep -GradleArgs $args -LogPath $instLog -Label 'Instrumented tests'

    $instReport = 'app\build\reports\androidTests\connected\debug'
    if (-not (Test-Path $instReport)) { $instReport = 'app\build\reports\androidTests\connected' }
    if (Test-Path $instReport) {
        Copy-Item $instReport (Join-Path $reportDir 'instrumented-html') -Recurse -Force
        $instSummary = Get-TestSummary $instReport
    }

    # Pull failure screenshots, if any
    $remoteShots = "/sdcard/Android/data/$Script:AppPkg/files/test-failures"
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $Script:Adb shell "ls $remoteShots" 2>$null | Out-Null
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($LASTEXITCODE -eq 0) {
        $localShots = Join-Path $reportDir 'screenshots'
        New-Item -ItemType Directory -Path $localShots -Force | Out-Null
        & $Script:Adb pull $remoteShots $localShots 2>&1 | Out-Null
        Write-Ok "Screenshots -> $localShots"
    }
} elseif ($SkipInstrumented) {
    Write-Section "INSTRUMENTED TESTS"
    Write-Info 'Skipped (--SkipInstrumented).'
}

# ---------- Monkey smoke ----------
if ($Monkey -gt 0 -and -not $SkipInstrumented) {
    Write-Section "MONKEY SMOKE ($Monkey events)"
    if ($Monkey -gt 5000) {
        Write-Bad "Refusing >5000 events on a real device. Capping to 5000."
        $Monkey = 5000
    }
    & $Script:Adb shell monkey -p $Script:AppPkg --throttle 50 -s 42 -v $Monkey 2>&1 | Tee-Object -FilePath $monkLog | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Ok "Monkey OK (no crashes)"
    } else {
        Write-Bad "Monkey crashed. See $monkLog"
        $instOk = $false
    }
}

# ---------- Summary ----------
Write-Section "SUMMARY"
$reportIndex = Join-Path $reportDir 'instrumented-html\index.html'
if (-not (Test-Path $reportIndex)) {
    $reportIndex = Join-Path $reportDir 'unit-html\index.html'
}

Write-Host ''
if ($unitOk -and $instOk) {
    Write-Host "  [OK] ALL GREEN" -ForegroundColor Green
} else {
    Write-Host "  [FAIL] FAILED - see logs in $reportDir" -ForegroundColor Red
}
Write-Host ''
Write-Host ("  Unit:         {0}" -f $(if ($SkipUnit) {'skipped'} elseif ($unitOk) {'passed'} else {'FAILED'})) -ForegroundColor White
Write-Host ("  Build APKs:   {0}" -f $(if ($SkipInstrumented) {'skipped'} elseif ($buildOk) {'passed'} else {'FAILED'})) -ForegroundColor White
if (-not $SkipInstrumented -and $instSummary.ok) {
    Write-Host ("  Instrumented: passed={0} failed={1} skipped={2}" -f $instSummary.passed,$instSummary.failed,$instSummary.skipped) -ForegroundColor White
}
if ($Monkey -gt 0) {
    Write-Host ("  Monkey:       {0} events ({1})" -f $Monkey, $(if ($instOk) {'no crashes'} else {'crashed'})) -ForegroundColor White
}
Write-Host ''
Write-Host "  Report: $reportDir" -ForegroundColor Cyan
if (Test-Path $reportIndex) { Write-Host "  HTML:   $reportIndex" -ForegroundColor Cyan }

# Single-line machine-grep summary (do not change the format)
"TESTS: passed={0} failed={1} skipped={2} report=`"{3}`"" -f `
    $instSummary.passed, $instSummary.failed, $instSummary.skipped, $reportDir | Write-Host

if ($OpenReport -and (Test-Path $reportIndex)) {
    Start-Process $reportIndex
}

# Exit code
if ($unitOk -and $instOk -and ($instSummary.failed -eq 0)) {
    exit 0
} else {
    exit 1
}
