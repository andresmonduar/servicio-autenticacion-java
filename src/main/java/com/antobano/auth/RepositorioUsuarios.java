package com.antobano.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacén de usuarios del servicio.
 *
 * <p>Mantiene los usuarios en memoria para consultarlos rápido y los guarda en
 * un archivo de texto para que no se pierdan al reiniciar el servicio.
 * Formato de cada línea del archivo: {@code usuario;sal;hash}.</p>
 */
public class RepositorioUsuarios {

    /** Separador de campos en el archivo (no aparece en usuarios ni en Base64). */
    private static final String SEPARADOR = ";";

    private final Path archivo;
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();

    /**
     * Crea el repositorio y carga los usuarios que ya estén guardados.
     *
     * @param archivo ruta del archivo donde se guardan los usuarios
     * @throws IOException si no se puede leer o crear el archivo
     */
    public RepositorioUsuarios(Path archivo) throws IOException {
        this.archivo = archivo;
        cargarDesdeArchivo();
    }

    /** Lee el archivo (si existe) y llena el mapa en memoria. */
    private void cargarDesdeArchivo() throws IOException {
        Path carpeta = archivo.toAbsolutePath().getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
        if (!Files.exists(archivo)) {
            return;
        }
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        for (String linea : lineas) {
            String[] partes = linea.split(SEPARADOR);
            if (partes.length == 3) {
                usuarios.put(partes[0], new Usuario(partes[0], partes[1], partes[2]));
            }
        }
    }

    /**
     * Busca un usuario por su nombre.
     *
     * @param nombreUsuario nombre ya normalizado (minúsculas)
     * @return el usuario, o vacío si no existe
     */
    public Optional<Usuario> buscar(String nombreUsuario) {
        return Optional.ofNullable(usuarios.get(nombreUsuario));
    }

    /**
     * Agrega un usuario nuevo si el nombre no está ocupado.
     *
     * @param usuario usuario a guardar
     * @return {@code true} si se guardó; {@code false} si el nombre ya existía
     * @throws IOException si falla la escritura en el archivo
     */
    public synchronized boolean agregar(Usuario usuario) throws IOException {
        if (usuarios.containsKey(usuario.nombreUsuario())) {
            return false;
        }
        String linea = String.join(SEPARADOR, usuario.nombreUsuario(), usuario.sal(), usuario.hash())
                + System.lineSeparator();
        Files.writeString(archivo, linea, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        usuarios.put(usuario.nombreUsuario(), usuario);
        return true;
    }
}
