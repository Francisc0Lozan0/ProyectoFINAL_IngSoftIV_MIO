# Script de verificación rápida antes de iniciar la aplicación
# Verifica: 1) Archivos de datos, 2) Configuración, 3) Compilación

Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  SITM-MIO Pre-Launch Verification" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$projectDir = "c:\Users\franc\OneDrive - Universidad Icesi\semestre6_ing.sistemas\ingSOFT\proyectoFinalSantiago\SANTAIGO\SANTAIGO\sitm-mio"
Set-Location $projectDir

# 1. Verificar archivo de datos
Write-Host "1️⃣  Verificando archivo de datos..." -ForegroundColor Yellow
$dataFile = "results\velocities_10_MILLONES_20251130_221839.csv"
if (Test-Path $dataFile) {
    $fileSize = (Get-Item $dataFile).Length / 1MB
    $lineCount = (Get-Content $dataFile | Measure-Object -Line).Lines
    Write-Host "   ✅ Archivo encontrado: $dataFile" -ForegroundColor Green
    Write-Host "      Tamaño: $([math]::Round($fileSize, 2)) MB" -ForegroundColor Gray
    Write-Host "      Líneas: $lineCount" -ForegroundColor Gray
} else {
    Write-Host "   ❌ ERROR: No se encontró el archivo de datos" -ForegroundColor Red
    Write-Host "      Esperado en: $dataFile" -ForegroundColor Gray
    exit 1
}
Write-Host ""

# 2. Verificar archivos de configuración
Write-Host "2️⃣  Verificando archivos de configuración..." -ForegroundColor Yellow
$configFiles = @(
    "src\main\resources\application.properties",
    "src\main\java\com\sitm\mio\config\SpringBootConfig.java",
    "src\main\java\com\sitm\mio\config\DataInitializer.java",
    "src\main\java\com\sitm\mio\loader\VelocityDataLoader.java"
)

$allConfigOk = $true
foreach ($file in $configFiles) {
    if (Test-Path $file) {
        Write-Host "   ✅ $file" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Falta: $file" -ForegroundColor Red
        $allConfigOk = $false
    }
}

if (-not $allConfigOk) {
    Write-Host ""
    Write-Host "   ❌ ERROR: Faltan archivos de configuración" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 3. Verificar compilación
Write-Host "3️⃣  Verificando compilación..." -ForegroundColor Yellow
mvn clean compile -q 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Proyecto compila correctamente" -ForegroundColor Green
} else {
    Write-Host "   ❌ ERROR: Problemas de compilación" -ForegroundColor Red
    Write-Host "      Ejecuta 'mvn clean compile' para ver detalles" -ForegroundColor Gray
    exit 1
}
Write-Host ""

# 4. Verificar estado de base de datos
Write-Host "4️⃣  Verificando base de datos..." -ForegroundColor Yellow
$dbFile = "data\sitm_mio.mv.db"
if (Test-Path $dbFile) {
    $dbSize = (Get-Item $dbFile).Length / 1MB
    Write-Host "   ℹ️  Base de datos existente: $([math]::Round($dbSize, 2)) MB" -ForegroundColor Yellow
    Write-Host "      Los datos NO se recargarán (inicio rápido)" -ForegroundColor Gray
    Write-Host "      Para recargar, elimina: data\sitm_mio.*" -ForegroundColor Gray
} else {
    Write-Host "   ℹ️  No existe base de datos" -ForegroundColor Yellow
    Write-Host "      Se cargarán ~530K registros (puede tomar 30-60 seg)" -ForegroundColor Gray
}
Write-Host ""

# 5. Resumen
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✅ Verificación completada - Todo listo para iniciar" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Próximos pasos:" -ForegroundColor Yellow
Write-Host "   1. Iniciar aplicación: .\scripts\start_springboot_app.ps1" -ForegroundColor White
Write-Host "   2. Abrir Dashboard: http://localhost:8080/dashboard.html" -ForegroundColor White
Write-Host "   3. Ver API: http://localhost:8080/api/data/stats" -ForegroundColor White
Write-Host ""
Write-Host "📚 Documentación completa en: sitm-mio\README_SPRING_BOOT.md" -ForegroundColor Cyan
Write-Host ""
