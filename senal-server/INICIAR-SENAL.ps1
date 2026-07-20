$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "Señal Server PRO"
Set-Location $PSScriptRoot
Write-Host ""
Write-Host "SEÑAL SERVER PRO" -ForegroundColor Cyan
Write-Host "Panel: http://localhost:3000" -ForegroundColor Green
Write-Host "No cierres esta ventana mientras uses el servidor." -ForegroundColor Yellow
Write-Host ""
npm start
