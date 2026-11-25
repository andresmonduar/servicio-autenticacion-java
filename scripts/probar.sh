#!/usr/bin/env bash
# Pruebas del servicio con curl. El servicio debe estar corriendo.
# Uso: ./scripts/probar.sh [url_base]     (por defecto http://localhost:8080)

BASE="${1:-http://localhost:8080}"
USUARIO="prueba_$(date +%s)"
CLAVE="clave123"

# Envía una petición e imprime el estado HTTP y el cuerpo de la respuesta.
# Argumentos: descripción, método, ruta, cuerpo JSON (opcional)
llamar() {
    local descripcion="$1" metodo="$2" ruta="$3" cuerpo="$4"
    echo "--- $descripcion"
    curl -s -w '\n    HTTP %{http_code}\n' -X "$metodo" "$BASE$ruta" \
        -H 'Content-Type: application/json' ${cuerpo:+-d "$cuerpo"}
}

llamar "1. Servicio en línea"                        GET  /api/salud
llamar "2. Registro correcto"                        POST /api/registro "{\"usuario\":\"$USUARIO\",\"contrasena\":\"$CLAVE\"}"
llamar "3. Registro repetido (debe dar 409)"         POST /api/registro "{\"usuario\":\"$USUARIO\",\"contrasena\":\"$CLAVE\"}"
llamar "4. Registro con contraseña corta (400)"      POST /api/registro "{\"usuario\":\"otro_usuario\",\"contrasena\":\"123\"}"
llamar "5. Login correcto (200)"                     POST /api/login    "{\"usuario\":\"$USUARIO\",\"contrasena\":\"$CLAVE\"}"
llamar "6. Login con contraseña incorrecta (401)"    POST /api/login    "{\"usuario\":\"$USUARIO\",\"contrasena\":\"incorrecta\"}"
llamar "7. Login con usuario inexistente (401)"      POST /api/login    "{\"usuario\":\"no_existe_xyz\",\"contrasena\":\"$CLAVE\"}"
llamar "8. Login sin contraseña (400)"               POST /api/login    "{\"usuario\":\"$USUARIO\"}"
llamar "9. JSON mal formado (400)"                   POST /api/login    "{usuario: esto no es json"
llamar "10. Método no permitido (405)"               GET  /api/login
llamar "11. Ruta que no existe (404 en JSON)"        GET  /no_existe

echo "--- 12. Página web de login (debe dar 200 y ser HTML)"
curl -s -o /dev/null -w '    HTTP %{http_code}  tipo: %{content_type}\n' "$BASE/"
