#!/usr/bin/env bash
set -euo pipefail

# Ejecuta PerformanceClient con tamaños configurados y guarda salidas.
# Uso: ./scripts/run_experiment.sh <data_path>

DATA_PATH=${1:-./sitm-mio/data}

JAR="sitm-mio/target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR" ]; then
  echo "Jar not found: $JAR - build first (mvn package)"
  exit 1
fi

SIZES=(100000 1000000 10000000)
OUT_DIR=doc/experiments
mkdir -p "$OUT_DIR"

for s in "${SIZES[@]}"; do
  echo "Running experiment size=$s"
  ts=$(date +%s)
  java -cp "$JAR" com.sitm.mio.client.PerformanceClient "$DATA_PATH" > "$OUT_DIR/experiment_${s}_${ts}.log" 2>&1 || true
  echo "Logged to $OUT_DIR/experiment_${s}_${ts}.log"
done

echo "Experiments finished."
