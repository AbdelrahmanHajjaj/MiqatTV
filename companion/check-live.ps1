param(
    [ValidatePattern('^(emulator-[0-9]+|127\.0\.0\.1:[0-9]+)$')][string]$Phone = '127.0.0.1:5581',
    [ValidatePattern('^(emulator-[0-9]+|127\.0\.0\.1:[0-9]+)$')][string]$Tv = '127.0.0.1:5583',
    [ValidateRange(1024,65535)][int]$ServerPort = 15037,
    [string]$Adb = ''
)
$ErrorActionPreference = 'Stop'
if ($Phone -eq $Tv) { throw 'Select two different owned emulators.' }
if (!$Adb) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $Adb = Join-Path $sdk 'platform-tools/adb.exe'
}
$root = Split-Path $PSScriptRoot -Parent
$evidence = Join-Path $root 'state/companion'
New-Item -ItemType Directory -Force $evidence | Out-Null
function Invoke-FixtureAdb([string[]]$CommandArgs) {
    $output = & $Adb -P $ServerPort @CommandArgs
    if ($LASTEXITCODE -ne 0) { throw 'An emulator fixture command failed.' }
    return $output
}
function Test-PhoneMarker([string]$Name) {
    & $Adb -P $ServerPort -s $Phone shell run-as tv.miqat.companion test -f "files/$Name" 2>$null
    return $LASTEXITCODE -eq 0
}
foreach ($serial in @($Phone,$Tv)) {
    if ($serial.StartsWith('127.0.0.1:')) { Invoke-FixtureAdb -CommandArgs @('connect',$serial) | Out-Null }
    $qemu = Invoke-FixtureAdb -CommandArgs @('-s',$serial,'shell','getprop','ro.kernel.qemu')
    if (($qemu -join '').Trim() -ne '1') { throw 'This test refuses physical devices.' }
}
$port = (Get-Content -Raw (Join-Path $PSScriptRoot 'protocol/lan.json') | ConvertFrom-Json).port
$forwards = Invoke-FixtureAdb -CommandArgs @('forward','--list')
if ($forwards | Where-Object { ($_ -split '\s+')[1] -eq "tcp:$port" }) { throw 'The fixture bridge port is already in use.' }
$hostLog = Join-Path $evidence 'tv-two-emulator.txt'
$phoneLog = Join-Path $evidence 'phone-two-emulator.txt'
$tvHost = $null
$bridge = $false
try {
    $tvHost = Start-Process -FilePath $Adb -ArgumentList @('-P',$ServerPort,'-s',$Tv,'shell','am','instrument','-w','-r','-e','check','companion-host','tv.miqat.home.test/tv.miqat.home.AppearanceCycleDeviceCheck') -WindowStyle Hidden -RedirectStandardOutput $hostLog -RedirectStandardError (Join-Path $evidence 'tv-two-emulator.stderr.txt') -PassThru
    $end = (Get-Date).AddSeconds(30)
    do {
        & $Adb -P $ServerPort -s $Tv shell run-as tv.miqat.home test -f files/companion-host-qr.json 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        $tvHost.Refresh()
        if ($tvHost.HasExited -or (Get-Date) -gt $end) { throw 'TV fixture did not become ready.' }
        Start-Sleep -Milliseconds 200
    } while ($true)
    $raw = Invoke-FixtureAdb -CommandArgs @('-s',$Tv,'exec-out','run-as','tv.miqat.home','cat','files/companion-host-qr.json')
    $qr = ($raw -join "`n") | ConvertFrom-Json
    if ($qr.type -ne 'miqat-tv-pair' -or $qr.version -ne 3) { throw 'Unexpected TV fixture QR.' }
    # Emulator NAT needs a local bridge; certificate, pin, token and both apps stay real.
    $qr.url = "https://10.0.2.2:$port"
    $fixtureQr = Join-Path $evidence 'bridge-qr.json'
    $qr | ConvertTo-Json -Compress -Depth 8 | Set-Content $fixtureQr -Encoding utf8NoBOM
    & node -e "const fs=require('fs'),codec=require('./companion/qr');const file=process.argv[1];fs.writeFileSync(file,codec.encode(JSON.parse(fs.readFileSync(file,'utf8'))))" $fixtureQr
    if ($LASTEXITCODE -ne 0) { throw 'Compact fixture QR encoding failed.' }
    Invoke-FixtureAdb -CommandArgs @('-s',$Tv,'forward',"tcp:$port","tcp:$port") | Out-Null
    $bridge = $true
    Invoke-FixtureAdb -CommandArgs @('-s',$Phone,'push',$fixtureQr,'/data/local/tmp/companion-live-qr.json') | Out-Null
    Invoke-FixtureAdb -CommandArgs @('-s',$Phone,'shell','run-as','tv.miqat.companion','cp','/data/local/tmp/companion-live-qr.json','files/companion-live-qr.json') | Out-Null
    $job = Start-Process -FilePath $Adb -ArgumentList @('-P',$ServerPort,'-s',$Phone,'shell','am','instrument','-w','-e','suite','live','tv.miqat.companion.test/tv.miqat.companion.CompanionCheck') -WindowStyle Hidden -RedirectStandardOutput $phoneLog -RedirectStandardError (Join-Path $evidence 'phone-two-emulator.stderr.txt') -PassThru
    $end = (Get-Date).AddSeconds(120)
    while (!$job.HasExited -and (Get-Date) -lt $end) {
        if ($bridge -and (Test-PhoneMarker 'companion-ui-offline-ready')) {
            Invoke-FixtureAdb -CommandArgs @('-s',$Tv,'forward','--remove',"tcp:$port") | Out-Null
            $bridge = $false
            Invoke-FixtureAdb -CommandArgs @('-s',$Phone,'shell','run-as','tv.miqat.companion','touch','files/companion-ui-offline-go') | Out-Null
        }
        Start-Sleep -Milliseconds 500
        $job.Refresh()
    }
    if (!$job.HasExited) { throw 'Phone integration exceeded its bounded test window.' }
    if ((Get-Content -Raw $phoneLog) -notmatch 'PASS: phone UI paired') { throw "Phone integration failed; inspect $phoneLog" }
} finally {
    if ($tvHost) {
        Invoke-FixtureAdb -CommandArgs @('-s',$Tv,'shell','run-as','tv.miqat.home','touch','files/companion-host-stop') | Out-Null
        if (!$tvHost.WaitForExit(30000)) { throw 'TV host did not stop; its own 180-second bound remains active.' }
    }
    if ($bridge) { Invoke-FixtureAdb -CommandArgs @('-s',$Tv,'forward','--remove',"tcp:$port") | Out-Null }
    Invoke-FixtureAdb -CommandArgs @('-s',$Phone,'shell','rm','-f','/data/local/tmp/companion-live-qr.json') | Out-Null
}
if ((Get-Content -Raw $hostLog) -notmatch 'INSTRUMENTATION_CODE: -1') { throw "TV fixture cleanup failed; inspect $hostLog" }
Write-Output 'PASS: real phone/TV apps in two owned emulators; injected approval, local bridge cut, credentials and preferences cleaned up.'
