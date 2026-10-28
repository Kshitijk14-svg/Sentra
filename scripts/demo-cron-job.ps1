# Demo scheduled job for Sentra's CRON checks, run every minute by Windows Task Scheduler.
#
# It "backs up" the Sentra docs folder into a timestamped zip and reports to Sentra:
#   /ping/<token>/start  when it begins
#   /ping/<token>        when the backup succeeded
#   /ping/<token>/fail   when it failed
#
# To demo a failing job, create the file named in $FailFlag; delete it to recover.
# To demo a job that stopped running entirely, disable the task:
#   schtasks /Change /TN "SentraDemoJob" /DISABLE

param(
    [string]$Token = "Rvb6SUHQtIF6BcndHBzMVEPBnSp-2oxq",
    [string]$Sentra = "http://localhost:8081"
)

$PingUrl  = "$Sentra/ping/$Token"
$Source   = Join-Path $PSScriptRoot "..\docs"
$Backups  = Join-Path $env:TEMP "sentra-demo-backups"
$FailFlag = Join-Path $Backups "FAIL"
$Log      = Join-Path $Backups "job.log"

function Ping([string]$suffix) {
    try { Invoke-RestMethod "$PingUrl$suffix" -TimeoutSec 10 | Out-Null } catch { }
}

New-Item -ItemType Directory -Force $Backups | Out-Null
Ping "/start"
try {
    if (Test-Path $FailFlag) { throw "simulated failure ($FailFlag exists)" }
    $zip = Join-Path $Backups ("docs-{0:yyyyMMdd-HHmmss}.zip" -f (Get-Date))
    Compress-Archive -Path "$Source\*" -DestinationPath $zip -Force
    # Keep only the 10 newest backups.
    Get-ChildItem $Backups -Filter "docs-*.zip" | Sort-Object LastWriteTime -Descending |
        Select-Object -Skip 10 | Remove-Item -Force
    Ping ""
    Add-Content $Log ("{0:u} OK   {1}" -f (Get-Date), (Split-Path $zip -Leaf))
} catch {
    Ping "/fail"
    Add-Content $Log ("{0:u} FAIL {1}" -f (Get-Date), $_.Exception.Message)
}
