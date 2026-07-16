$ErrorActionPreference = "Stop"
$Host.UI.RawUI.WindowTitle = "Señal Server - Instalador"

Write-Host ""
Write-Host "======================================" -ForegroundColor Cyan
Write-Host "   SEÑAL SERVER PRO - INSTALADOR" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "Node.js no está instalado." -ForegroundColor Yellow
    Write-Host "Instalando Node.js LTS..."
    winget install --id OpenJS.NodeJS.LTS --exact --accept-package-agreements --accept-source-agreements
    Write-Host "Cierra PowerShell, abre uno nuevo y ejecuta otra vez este instalador." -ForegroundColor Green
    Read-Host "Presiona Enter"
    exit
}

Set-Location $PSScriptRoot

if (-not (Test-Path ".env")) {
    Copy-Item ".env.example" ".env"
    Write-Host "Se creó .env. Cambia las claves y la contraseña master." -ForegroundColor Yellow
    notepad .env
    Read-Host "Guarda el archivo y presiona Enter para continuar"
}

Write-Host "Instalando dependencias..." -ForegroundColor Cyan
npm install

try {
    New-NetFirewallRule -DisplayName "Senal Server 3000" -Direction Inbound -Protocol TCP -LocalPort 3000 -Action Allow -ErrorAction SilentlyContinue | Out-Null
} catch {
    Write-Host "No se pudo crear la regla del firewall automáticamente." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Instalación terminada." -ForegroundColor Green
Write-Host "Inicia con INICIAR-SENAL.bat" -ForegroundColor Cyan
Read-Host "Presiona Enter"
