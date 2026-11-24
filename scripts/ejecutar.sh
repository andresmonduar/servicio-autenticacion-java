#!/usr/bin/env bash
# Compila y ejecuta el servicio.
# Uso: ./scripts/ejecutar.sh [puerto]     (por defecto 8080)
set -e
cd "$(dirname "$0")/.."

./scripts/compilar.sh
java -cp out com.antobano.auth.Main "$@"
