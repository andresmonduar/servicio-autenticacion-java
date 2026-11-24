package com.antobano.auth;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Punto de entrada de la aplicación.
 *
 * <p>Uso: {@code java -cp out com.antobano.auth.Main [puerto]}.
 * Si no se indica puerto se usa la variable de entorno PUERTO o, en su defecto, el 8080.</p>
 */
public class Main {

    private static final int PUERTO_POR_DEFECTO = 8080;
    private static final Path ARCHIVO_USUARIOS = Path.of("datos", "usuarios.csv");

    public static void main(String[] args) throws IOException {
        int puerto = leerPuerto(args);

        RepositorioUsuarios repositorio = new RepositorioUsuarios(ARCHIVO_USUARIOS);
        ServicioAutenticacion servicio = new ServicioAutenticacion(repositorio);
        ServidorAuth servidor = new ServidorAuth(puerto, servicio);

        servidor.iniciar();
        System.out.println("Servicio de autenticación en http://localhost:" + puerto);
        System.out.println("  POST /api/registro   POST /api/login   GET /api/salud");
        System.out.println("Presiona Ctrl+C para detenerlo.");
    }

    /** Toma el puerto del primer argumento, de la variable PUERTO o del valor por defecto. */
    private static int leerPuerto(String[] args) {
        String texto = args.length > 0 ? args[0] : System.getenv("PUERTO");
        if (texto == null || texto.isBlank()) {
            return PUERTO_POR_DEFECTO;
        }
        try {
            int puerto = Integer.parseInt(texto.trim());
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
            return puerto;
        } catch (NumberFormatException e) {
            System.err.println("Puerto no válido '" + texto + "'. Se usará el " + PUERTO_POR_DEFECTO + ".");
            return PUERTO_POR_DEFECTO;
        }
    }
}
