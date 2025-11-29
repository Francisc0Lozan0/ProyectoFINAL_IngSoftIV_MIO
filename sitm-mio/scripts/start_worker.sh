#!/bin/bash

if [ $# -lt 2 ]; then
    echo "Usage: $0 <worker_id> <master_host>"
    echo "Example: $0 worker1 localhost"
    exit 1
fi

WORKER_ID=$1
MASTER_HOST=$2
MASTER_ENDPOINT="tcp -h $MASTER_HOST -p 10000"

echo "Iniciando Worker $WORKER_ID conectando a $MASTER_HOST"

java -cp target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar com.sitm.mio.worker.WorkerServer "$WORKER_ID" "$MASTER_ENDPOINT"