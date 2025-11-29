#!/bin/bash

if [ $# -lt 2 ]; then
    echo "Usage: $0 <worker_id> <master_host>"
    echo "Example: $0 worker1 192.168.1.100"
    exit 1
fi

WORKER_ID=$1
MASTER_HOST=$2
MASTER_ENDPOINT="tcp -h $MASTER_HOST -p 10000"

echo "Iniciando Worker $WORKER_ID conectando a $MASTER_HOST"

java -cp target/classes com.sitm.mio.worker.DistributedWorkerServer "$WORKER_ID" "$MASTER_ENDPOINT"