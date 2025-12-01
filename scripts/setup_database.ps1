# Setup Script para SITM-MIO con PostgreSQL
# Para Windows PowerShell

Write-Host "🚀 Configurando SITM-MIO con PostgreSQL" -ForegroundColor Green

# 1. Verificar PostgreSQL
Write-Host "`n1️⃣ Verificando PostgreSQL..." -ForegroundColor Cyan
try {
    $pgVersion = psql --version
    Write-Host "   ✅ PostgreSQL encontrado: $pgVersion" -ForegroundColor Green
} catch {
    Write-Host "   ❌ PostgreSQL no encontrado. Por favor instala PostgreSQL primero." -ForegroundColor Red
    Write-Host "   Descarga desde: https://www.postgresql.org/download/windows/" -ForegroundColor Yellow
    exit 1
}

# 2. Crear base de datos
Write-Host "`n2️⃣ Creando base de datos sitm_mio..." -ForegroundColor Cyan
$createDb = @"
SELECT 'CREATE DATABASE sitm_mio'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'sitm_mio')\gexec
"@

echo $createDb | psql -U postgres -q 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Base de datos creada o ya existe" -ForegroundColor Green
} else {
    Write-Host "   ⚠️ Verifica que PostgreSQL esté corriendo y las credenciales sean correctas" -ForegroundColor Yellow
}

# 3. Ejecutar schema
Write-Host "`n3️⃣ Creando tablas..." -ForegroundColor Cyan
$schemaPath = "config\db_schema.sql"
if (Test-Path $schemaPath) {
    psql -U postgres -d sitm_mio -f $schemaPath -q
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✅ Tablas creadas exitosamente" -ForegroundColor Green
    } else {
        Write-Host "   ❌ Error creando tablas" -ForegroundColor Red
    }
} else {
    Write-Host "   ❌ No se encontró el archivo db_schema.sql en config/" -ForegroundColor Red
}

# 4. Compilar proyecto Maven
Write-Host "`n4️⃣ Compilando proyecto con Maven..." -ForegroundColor Cyan
mvn clean install -q
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Proyecto compilado exitosamente" -ForegroundColor Green
} else {
    Write-Host "   ❌ Error compilando proyecto" -ForegroundColor Red
}

# 5. Verificar configuración
Write-Host "`n5️⃣ Verificando configuración..." -ForegroundColor Cyan
$propsPath = "src\main\resources\application.properties"
if (Test-Path $propsPath) {
    Write-Host "   ✅ application.properties encontrado" -ForegroundColor Green
    Write-Host "   📝 Verifica las credenciales en: $propsPath" -ForegroundColor Yellow
} else {
    Write-Host "   ❌ application.properties no encontrado" -ForegroundColor Red
}

# 6. Resumen
Write-Host "`n" + "="*60 -ForegroundColor Cyan
Write-Host "✅ CONFIGURACIÓN COMPLETA" -ForegroundColor Green
Write-Host "="*60 -ForegroundColor Cyan

Write-Host "`n📋 Próximos pasos:" -ForegroundColor Yellow
Write-Host "   1. Editar src\main\resources\application.properties con tus credenciales"
Write-Host "   2. Actualizar imports: VelocityFileManager -> VelocityPersistenceAdapter"
Write-Host "   3. Ejecutar tu aplicación y verificar que los datos se guardan en PostgreSQL"
Write-Host "   4. Consultar datos con: psql -U postgres -d sitm_mio"

Write-Host "`n📚 Más información en MIGRATION_GUIDE.md" -ForegroundColor Cyan
Write-Host ""
