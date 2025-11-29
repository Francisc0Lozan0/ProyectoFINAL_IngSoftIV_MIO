#!/bin/bash

echo "Iniciando Master Server SITM-MIO"

# Configuración
DATA_PATH="/ruta/a/tus/archivos/csv"
MASTER_ENDPOINT="tcp -p 10000"

java -cp target/classes com.sitm.mio.master.DistributedMasterServer "$DATA_PATH"