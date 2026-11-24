package com.antobano.auth;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidad mínima para trabajar con JSON sin librerías externas.
 *
 * <p>Solo entiende objetos planos cuyos valores son texto, por ejemplo
 * {@code {"usuario":"ana","contrasena":"abc123"}}, que es lo único que
 * necesita este servicio.</p>
 */
public final class JsonSimple {

    private JsonSimple() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Convierte un texto JSON (objeto plano de textos) en un mapa clave-valor.
     *
     * @throws IllegalArgumentException si el texto no es un JSON válido de este tipo
     */
    public static Map<String, String> parsearObjetoPlano(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("El cuerpo de la petición está vacío.");
        }
        Lector lector = new Lector(texto);
        Map<String, String> resultado = new LinkedHashMap<>();

        lector.esperar('{');
        if (lector.mirar() == '}') {
            lector.avanzar();
        } else {
            while (true) {
                String clave = lector.leerCadena();
                lector.esperar(':');
                if (lector.mirar() != '"') {
                    throw new IllegalArgumentException("Solo se admiten valores de tipo texto.");
                }
                String valor = lector.leerCadena();
                if (resultado.put(clave, valor) != null) {
                    throw new IllegalArgumentException("El campo '" + clave + "' está repetido.");
                }
                char siguiente = lector.mirar();
                lector.avanzar();
                if (siguiente == '}') {
                    break;
                }
                if (siguiente != ',') {
                    throw new IllegalArgumentException("JSON mal formado.");
                }
            }
        }
        lector.verificarFin();
        return resultado;
    }

    /** Construye la respuesta estándar del servicio: {@code {"exito":true,"mensaje":"..."}}. */
    public static String respuesta(boolean exito, String mensaje) {
        return "{\"exito\":" + exito + ",\"mensaje\":\"" + escapar(mensaje) + "\"}";
    }

    /** Escapa un texto para poder ponerlo dentro de comillas en un JSON. */
    public static String escapar(String texto) {
        StringBuilder sb = new StringBuilder();
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Recorre el texto carácter por carácter mientras se interpreta el JSON. */
    private static final class Lector {
        private final String texto;
        private int posicion = 0;

        Lector(String texto) {
            this.texto = texto;
        }

        private void saltarEspacios() {
            while (posicion < texto.length() && Character.isWhitespace(texto.charAt(posicion))) {
                posicion++;
            }
        }

        /** Devuelve el siguiente carácter sin consumirlo (o '\0' si se acabó el texto). */
        char mirar() {
            saltarEspacios();
            return posicion < texto.length() ? texto.charAt(posicion) : '\0';
        }

        void avanzar() {
            posicion++;
        }

        void esperar(char esperado) {
            if (mirar() != esperado) {
                throw new IllegalArgumentException("JSON mal formado: se esperaba '" + esperado + "'.");
            }
            posicion++;
        }

        void verificarFin() {
            saltarEspacios();
            if (posicion < texto.length()) {
                throw new IllegalArgumentException("JSON mal formado: hay texto de más al final.");
            }
        }

        /** Lee un texto entre comillas, resolviendo los escapes (\n, \", \\, \\uXXXX...). */
        String leerCadena() {
            esperar('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (posicion >= texto.length()) {
                    throw new IllegalArgumentException("JSON mal formado: texto sin cerrar.");
                }
                char c = texto.charAt(posicion++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    throw new IllegalArgumentException("JSON mal formado: carácter de control no permitido.");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (posicion >= texto.length()) {
                    throw new IllegalArgumentException("JSON mal formado: escape incompleto.");
                }
                char escape = texto.charAt(posicion++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (posicion + 4 > texto.length()) {
                            throw new IllegalArgumentException("JSON mal formado: escape unicode incompleto.");
                        }
                        try {
                            sb.append((char) Integer.parseInt(texto.substring(posicion, posicion + 4), 16));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("JSON mal formado: escape unicode inválido.");
                        }
                        posicion += 4;
                    }
                    default -> throw new IllegalArgumentException("JSON mal formado: escape desconocido.");
                }
            }
        }
    }
}
