<#
.SYNOPSIS
    Stop Pyloros MCP Gateway.

.DESCRIPTION
    Finds the process listening on the Pyloros port and terminates it.
    Uses netstat + Get-Process so only the exact process bound to the port
    is affected — unrelated Java processes are left alone.

.PARAMETER Port
    TCP port to check (default: 8081).

.PARAMETER Force
    Kill the process immediately without confirmation prompt.

.EXAMPLE
    .\scripts\stop-pyloros.ps1
    .\scripts\stop-pyloros.ps1 -Port 8082 -Force
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [int]$Port  = 8081,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

Write-Host "Searching for process on port $Port ..." -ForegroundColor Cyan

# Find the PID bound to the port via netstat
$netLines = netstat -ano | Select-String "TCP\s+.*:$Port\s+.*LISTENING"

if (-not $netLines) {
    Write-Host "No process is listening on port $Port. Nothing to stop." -ForegroundColor Green
    exit 0
}

# Extract the PID (last column of the LISTENING line)
$pid_ = ($netLines[0] -split '\s+')[-1]
$pid_ = [int]$pid_.Trim()

$proc = Get-Process -Id $pid_ -ErrorAction SilentlyContinue
if (-not $proc) {
    Write-Host "PID $pid_ is no longer running. Nothing to stop." -ForegroundColor Green
    exit 0
}

Write-Host "Found: PID=$pid_  Name=$($proc.Name)  Port=$Port" -ForegroundColor Yellow

if (-not $Force -and -not $PSCmdlet.ShouldProcess("PID $pid_ ($($proc.Name))", "Stop")) {
    Write-Host "Aborted. Use -Force or confirm with -WhatIf to see what would happen." -ForegroundColor Yellow
    exit 0
}

Stop-Process -Id $pid_ -Force
Write-Host "Process PID=$pid_ stopped." -ForegroundColor Green

