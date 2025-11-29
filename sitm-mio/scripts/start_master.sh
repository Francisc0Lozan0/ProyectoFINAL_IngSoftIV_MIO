#!/bin/bash

DATA_PATH="./data"

echo "Iniciando Master Server SITM-MIO"

java -jar target/mio-1.0-SNAPSHOT-jar-with-dependencies.jar "$DATA_PATH"