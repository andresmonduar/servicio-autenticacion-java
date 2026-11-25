package com.antobano.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP del servicio web de autenticación.
 *
 * <p>Endpoints (todos devuelven JSON):</p>
 * <ul>
 *   <li>{@code POST /api/registro} - crea un usuario nuevo.</li>
 *   <li>{@code POST /api/login} - comprueba usuario y contraseña.</li>
 *   <li>{@code GET  /api/salud} - indica que el servicio está en línea.</li>
 *   <li>{@code GET  /} - página web con los formularios de registro e inicio de sesión.</li>
 * </ul>
 * Cualquier otra ruta responde 404 en JSON.
 */
public class ServidorAuth {

    /** Tamaño máximo aceptado para el cuerpo de una petición (en bytes). */
    private static final int TAMANO_MAXIMO_CUERPO = 4096;

    private final int puerto;
    private final ServicioAutenticacion servicio;
    private HttpServer servidor;

    /** Respuesta que un endpoint entrega al cliente. */
    private record Respuesta(int estado, boolean exito, String mensaje) {
    }

    /** Lógica propia de cada endpoint POST: recibe los campos del JSON y devuelve la respuesta. */
    @FunctionalInterface
    private interface AccionPost {
        Respuesta ejecutar(Map<String, String> campos) throws IOException;
    }

    /**
     * @param puerto   puerto TCP donde escuchará el servicio
     * @param servicio lógica de registro y autenticación
     */
    public ServidorAuth(int puerto, ServicioAutenticacion servicio) {
        this.puerto = puerto;
        this.servicio = servicio;
    }

    /** Arranca el servidor y registra las rutas. */
    public void iniciar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(puerto), 0);

        servidor.createContext("/api/registro", intercambio -> procesarPost(intercambio, this::registrar));
        servidor.createContext("/api/login", intercambio -> procesarPost(intercambio, this::iniciarSesion));
        servidor.createContext("/api/salud", this::salud);
        // La ruta "/" atiende la página de inicio y, para todo lo demás, el error 404.
        servidor.createContext("/", this::raiz);

        // Varios hilos para atender peticiones a la vez.
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();
    }

    /** Detiene el servidor (se usa en las pruebas y al cerrar la aplicación). */
    public void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Lógica de cada endpoint
    // ------------------------------------------------------------------

    /** POST /api/registro: valida los datos y crea el usuario. */
    private Respuesta registrar(Map<String, String> campos) throws IOException {
        String usuario = campos.get("usuario");
        String contrasena = campos.get("contrasena");

        String error = servicio.validarCredenciales(usuario, contrasena);
        if (error != null) {
            return new Respuesta(400, false, error);
        }
        return switch (servicio.registrar(usuario, contrasena)) {
            case EXITOSO -> new Respuesta(201, true, "Registro exitoso.");
            case USUARIO_YA_EXISTE -> new Respuesta(409, false, "El usuario ya existe.");
        };
    }

    /** POST /api/login: autenticación satisfactoria (200) o error en la autenticación (401). */
    private Respuesta iniciarSesion(Map<String, String> campos) {
        String usuario = campos.get("usuario");
        String contrasena = campos.get("contrasena");

        if (usuario == null || contrasena == null) {
            return new Respuesta(400, false, "Debe enviar los campos 'usuario' y 'contrasena'.");
        }
        if (servicio.autenticar(usuario, contrasena)) {
            return new Respuesta(200, true, "Autenticación satisfactoria.");
        }
        // Mismo mensaje si el usuario no existe o la contraseña es incorrecta.
        return new Respuesta(401, false, "Error en la autenticación.");
    }

    /** GET /api/salud: comprobación sencilla de que el servicio responde. */
    private void salud(HttpExchange intercambio) throws IOException {
        if (!"GET".equals(intercambio.getRequestMethod())) {
            responder(intercambio, new Respuesta(405, false, "Método no permitido. Use GET."), "GET");
            return;
        }
        responder(intercambio, new Respuesta(200, true, "Servicio en línea."), null);
    }

    /**
     * GET /: entrega la página web de login y registro (archivo web/index.html).
     * Cualquier otra ruta que no exista devuelve 404 en formato JSON.
     */
    private void raiz(HttpExchange intercambio) throws IOException {
        String ruta = intercambio.getRequestURI().getPath();
        boolean esPaginaInicio = "/".equals(ruta) || "/index.html".equals(ruta);

        if (!esPaginaInicio) {
            responder(intercambio, new Respuesta(404, false, "Ruta no encontrada."), null);
            return;
        }
        if (!"GET".equals(intercambio.getRequestMethod())) {
            responder(intercambio, new Respuesta(405, false, "Método no permitido. Use GET."), "GET");
            return;
        }
        try (InputStream archivo = ServidorAuth.class.getResourceAsStream("/web/index.html")) {
            if (archivo == null) {
                responder(intercambio, new Respuesta(404, false, "Página no encontrada."), null);
                return;
            }
            byte[] pagina = archivo.readAllBytes();
            intercambio.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            // La página solo puede cargar recursos propios y conectarse a este mismo servidor.
            intercambio.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'");
            intercambio.sendResponseHeaders(200, pagina.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(pagina);
            }
            System.out.println("GET " + ruta + " -> 200");
        }
    }

    // ------------------------------------------------------------------
    // Utilidades comunes
    // ------------------------------------------------------------------

    /**
     * Revisa el método HTTP, lee y convierte el JSON, ejecuta la acción y envía la respuesta.
     * Cualquier error inesperado se convierte en una respuesta 500 sin datos técnicos.
     */
    private void procesarPost(HttpExchange intercambio, AccionPost accion) throws IOException {
        try {
            if (!"POST".equals(intercambio.getRequestMethod())) {
                responder(intercambio, new Respuesta(405, false, "Método no permitido. Use POST."), "POST");
                return;
            }
            byte[] cuerpo = intercambio.getRequestBody().readNBytes(TAMANO_MAXIMO_CUERPO + 1);
            if (cuerpo.length > TAMANO_MAXIMO_CUERPO) {
                responder(intercambio, new Respuesta(413, false, "La petición es demasiado grande."), null);
                return;
            }
            Map<String, String> campos;
            try {
                campos = JsonSimple.parsearObjetoPlano(new String(cuerpo, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                responder(intercambio, new Respuesta(400, false, e.getMessage()), null);
                return;
            }
            responder(intercambio, accion.ejecutar(campos), null);
        } catch (Exception e) {
            System.err.println("Error interno: " + e);
            responder(intercambio, new Respuesta(500, false, "Error interno del servidor."), null);
        }
    }

    /** Envía una respuesta JSON y deja una línea de registro (sin datos sensibles) en la consola. */
    private void responder(HttpExchange intercambio, Respuesta respuesta, String metodoPermitido) throws IOException {
        byte[] bytes = JsonSimple.respuesta(respuesta.exito(), respuesta.mensaje()).getBytes(StandardCharsets.UTF_8);

        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (metodoPermitido != null) {
            intercambio.getResponseHeaders().set("Allow", metodoPermitido);
        }
        intercambio.sendResponseHeaders(respuesta.estado(), bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
        System.out.println(intercambio.getRequestMethod() + " " + intercambio.getRequestURI().getPath()
                + " -> " + respuesta.estado());
    }
}
