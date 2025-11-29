#!/bin/bash

MASTER_HOST="localhost"
WORKER_COUNT=4

echo "Desplegando cluster SITM-MIO local"
echo "Master: $MASTER_HOST"
echo "Workers: $WORKER_COUNT"

mvn clean compile assembly:single -DskipTests

echo "Iniciando Master..."
java -jar target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar ./data &

echo "Esperando 5 segundos para que el Master inicie..."
sleep 5

for i in $(seq 1 $WORKER_COUNT); do
    WORKER_ID="worker$i"
    echo "Iniciando $WORKER_ID"
    java -cp target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar com.sitm.mio.worker.WorkerServer "$WORKER_ID" "tcp -h $MASTER_HOST -p 10000" &
done

echo "Cluster desplegado. Master y $WORKER_COUNT workers ejecutándose."