#!/usr/bin/env bash
# Compila el servicio. Genera los archivos .class en la carpeta "out".
# Requiere JDK 17 o superior (comprobar con: javac -version).
set -e
cd "$(dirname "$0")/.."

rm -rf out
mkdir -p out
javac --release 17 -encoding UTF-8 -d out $(find src -name '*.java')
echo "Compilación terminada: archivos generados en la carpeta out/"
