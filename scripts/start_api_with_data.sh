#!/usr/bin/env bash
set -euo pipefail

# Script integrado: carga datos y levanta API server

cd "$(dirname "$0")/../sitm-mio"
JAR="target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR" ]; then
  echo "❌ JAR no encontrado. Ejecuta: mvn package"
  exit 1
fi

echo "🔄 Cargando datos de results/ a H2..."
java -cp "$JAR" com.sitm.mio.loader.ResultsLoader ./results &
LOADER_PID=$!

# Esperar a que termine la carga
wait $LOADER_PID

echo ""
echo "✅ Datos cargados"
echo "🚀 Iniciando API Server en puerto 8080..."
echo ""

# Ejecutar API server (comparte la misma JVM y memoria)
exec java -cp "$JAR" com.sitm.mio.api.ApiServer 8080
