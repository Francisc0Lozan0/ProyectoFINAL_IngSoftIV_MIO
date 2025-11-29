#!/bin/bash

# Script para desplegar cluster en múltiples máquinas
MASTER_HOST="192.168.1.100"  # IP del maestro
WORKER_HOSTS=("192.168.1.101" "192.168.1.102" "192.168.1.103" "192.168.1.104")

echo "Desplegando cluster SITM-MIO"
echo "Master: $MASTER_HOST"
echo "Workers: ${WORKER_HOSTS[@]}"

# Compilar proyecto
echo "Compilando proyecto..."
mvn clean compile

# Iniciar workers en cada máquina
for i in "${!WORKER_HOSTS[@]}"; do
    WORKER_ID="worker$((i+1))"
    HOST="${WORKER_HOSTS[$i]}"
    
    echo "Iniciando $WORKER_ID en $HOST"
    ssh $HOST "cd /ruta/del/proyecto && java -cp target/classes com.sitm.mio.worker.DistributedWorkerServer $WORKER_ID 'tcp -h $MASTER_HOST -p 10000'" &
done

echo "Cluster desplegado. Workers conectándose al master..."