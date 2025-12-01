#!/bin/bash

# Configuración
MASTER_HOST=10.147.20.122
MASTER_PORT=10000
API_PORT=8080

echo "=========================================="
echo "  SITM-MIO Enhanced API Server"
echo "=========================================="
echo ""
echo "Configuración:"
echo "  • Master Host: $MASTER_HOST"
echo "  • Master Port: $MASTER_PORT"
echo "  • API Port: $API_PORT"
echo ""

# Compilar si es necesario
if [ ! -f "target/worker.jar" ]; then
    echo "📦 Compilando proyecto..."
    mvn clean compile assembly:single -DskipTests
fi

# Crear directorio de resultados
mkdir -p ./results

echo ""
echo "🚀 Iniciando API Server..."
echo ""

java -cp target/worker.jar \
    com.sitm.mio.api.EnhancedApiServer \
    $MASTER_HOST \
    $MASTER_PORT \
    $API_PORT