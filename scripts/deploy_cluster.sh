#!/usr/bin/env bash
set -euo pipefail

# Despliega la BD (TimescaleDB) y arranca Master + N workers locales.
# Uso: ./scripts/deploy_cluster.sh <data_path> <num_workers>

DATA_PATH=${1:-./sitm-mio/data}
NUM_WORKERS=${2:-4}

echo "Starting database via docker-compose..."
docker-compose up -d db

echo "Building project..."
(cd sitm-mio && mvn -q -DskipTests package)

JAR="sitm-mio/target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR" ]; then
  echo "Jar not found: $JAR"
  exit 1
fi

echo "Starting Master..."
nohup java -jar "$JAR" "$DATA_PATH" > master.log 2>&1 &
MASTER_PID=$!
echo "Master PID: $MASTER_PID"

echo "Starting $NUM_WORKERS workers..."
for i in $(seq 1 $NUM_WORKERS); do
  echo "Starting worker $i"
  nohup java -cp "$JAR" com.sitm.mio.worker.WorkerServer "$i" > worker-$i.log 2>&1 &
  echo "Worker $i started"
done

echo "Starting API server on port 8080"
nohup java -cp "$JAR" com.sitm.mio.api.ApiServer 8080 > api.log 2>&1 &

echo "Deployment complete. Check master.log, api.log and worker-*.log for output."
