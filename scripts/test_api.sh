#!/bin/bash

API_URL="http://localhost:8080"

echo "=========================================="
echo "  SITM-MIO API Test Client"
echo "=========================================="
echo ""

# Colores
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. Test de estado del sistema
echo -e "${BLUE}[1/6]${NC} Testing system status..."
curl -s "$API_URL/api/system/status" | jq '.'
echo ""

# 2. Listar jobs existentes
echo -e "${BLUE}[2/6]${NC} Listing existing jobs..."
curl -s "$API_URL/api/historical/jobs" | jq '.'
echo ""

# 3. Iniciar procesamiento histórico
echo -e "${BLUE}[3/6]${NC} Starting historical processing..."
echo -e "${YELLOW}Nota: Ajusta el filePath según tu sistema${NC}"

RESPONSE=$(curl -s -X POST "$API_URL/api/historical/process" \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "./data/datagrams_1000.csv",
    "label": "TEST_API_1K"
  }')

echo "$RESPONSE" | jq '.'
JOB_ID=$(echo "$RESPONSE" | jq -r '.jobId')
echo ""

if [ "$JOB_ID" != "null" ] && [ ! -z "$JOB_ID" ]; then
    echo -e "${GREEN}Job creado: $JOB_ID${NC}"
    echo ""
    
    # 4. Monitorear estado del job
    echo -e "${BLUE}[4/6]${NC} Monitoring job status..."
    for i in {1..5}; do
        sleep 2
        STATUS=$(curl -s "$API_URL/api/historical/status/$JOB_ID")
        echo "Intento $i:"
        echo "$STATUS" | jq '{status, progress, processedCount, totalDatagrams}'
        
        JOB_STATUS=$(echo "$STATUS" | jq -r '.status')
        if [ "$JOB_STATUS" == "COMPLETED" ]; then
            echo -e "${GREEN}✓ Job completado!${NC}"
            break
        fi
    done
    echo ""
    
    # 5. Obtener resultados
    echo -e "${BLUE}[5/6]${NC} Fetching results..."
    curl -s "$API_URL/api/historical/results/$JOB_ID" | jq '. | length' | \
        awk '{print "Total results: " $1}'
    echo ""
fi

# 6. Obtener métricas de punto de corte
echo -e "${BLUE}[6/6]${NC} Fetching cutoff metrics..."
curl -s "$API_URL/api/metrics/cutoff" | jq '. | length' | \
    awk '{print "Cutoff data points: " $1}'
echo ""

# 7. Obtener velocidades
echo -e "${BLUE}[BONUS]${NC} Fetching all velocities..."
curl -s "$API_URL/api/velocities/all" | jq '. | length' | \
    awk '{print "Total arcs with velocity: " $1}'
echo ""

echo -e "${GREEN}=========================================="
echo -e "  Tests completados"
echo -e "==========================================${NC}"
echo ""
echo "Para ver detalles completos, usa:"
echo "  curl http://localhost:8080/api/historical/jobs | jq '.'"
echo "  curl http://localhost:8080/api/metrics/cutoff | jq '.'"
echo "  curl http://localhost:8080/api/velocities/all | jq '.'"