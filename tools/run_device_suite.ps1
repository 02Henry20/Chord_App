param(
    [Parameter(Mandatory=$true)][string]$Classes,
    [Parameter(Mandatory=$true)][string]$Output,
    [string]$Adb = 'adb',
    [string]$Serial = 'emulator-5554',
    [hashtable]$Arguments = @{}
)
$ErrorActionPreference = 'Stop'
$runnerArguments = @('-s', $Serial, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $Classes)
foreach ($key in $Arguments.Keys) { $runnerArguments += @('-e', [string]$key, [string]$Arguments[$key]) }
$runnerArguments += 'com.henry.chord.test/androidx.test.runner.AndroidJUnitRunner'
& $Adb @runnerArguments 2>&1 | Set-Content -LiteralPath $Output
$deviceExit = $LASTEXITCODE
$result = Get-Content -LiteralPath $Output -Raw
Get-Content -LiteralPath $Output -Tail 20
# am instrument can exit 0 even when JUnit fails. Require the actual test runner success summary.
if ($deviceExit -ne 0 -or $result -notmatch 'OK \(\d+ tests?\)' -or $result -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
    throw "Android test suite did not pass. Read $Output"
}
