# Script para compilar y ejecutar la aplicación Spring Boot con carga automática de datos
# Este script limpia, compila y ejecuta la aplicación que automáticamente cargará los datos de velocidades

Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SITM-MIO Spring Boot Application Starter" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Cambiar al directorio del proyecto
$projectDir = "c:\Users\franc\OneDrive - Universidad Icesi\semestre6_ing.sistemas\ingSOFT\proyectoFinalSantiago\SANTAIGO\SANTAIGO\sitm-mio"
Set-Location $projectDir

Write-Host "📁 Directorio del proyecto: $projectDir" -ForegroundColor Green
Write-Host ""

# Verificar que existe el archivo de datos
$dataFile = "results\velocities_10_MILLONES_20251130_221839.csv"
if (Test-Path $dataFile) {
    $fileSize = (Get-Item $dataFile).Length / 1MB
    Write-Host "✓ Archivo de datos encontrado: $dataFile" -ForegroundColor Green
    Write-Host "  Tamaño: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Gray
} else {
    Write-Host "⚠ Advertencia: No se encontró el archivo de datos: $dataFile" -ForegroundColor Yellow
}
Write-Host ""

# Limpiar compilaciones previas
Write-Host "🧹 Limpiando compilaciones previas..." -ForegroundColor Yellow
mvn clean | Out-Null

# Compilar el proyecto
Write-Host "🔨 Compilando el proyecto..." -ForegroundColor Yellow
mvn compile
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ Error durante la compilación" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "✓ Compilación exitosa" -ForegroundColor Green
Write-Host ""

# Mensaje informativo
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  Iniciando aplicación..." -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "ℹ️  Al iniciar, la aplicación:" -ForegroundColor Yellow
Write-Host "   1. Creará la base de datos H2 en ./data/sitm_mio.mv.db" -ForegroundColor Gray
Write-Host "   2. Cargará automáticamente los datos de velocidades desde results/" -ForegroundColor Gray
Write-Host "   3. Iniciará el servidor API en http://localhost:8080" -ForegroundColor Gray
Write-Host ""
Write-Host "📊 Dashboard: http://localhost:8080/dashboard.html" -ForegroundColor Cyan
Write-Host "🗺️  Mapa:      http://localhost:8080/map.html" -ForegroundColor Cyan
Write-Host ""
Write-Host "⌨️  Presiona Ctrl+C para detener el servidor" -ForegroundColor Yellow
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

# Ejecutar la aplicación Spring Boot
mvn spring-boot:run -Dspring-boot.run.mainClass=com.sitm.mio.config.SpringBootConfig
