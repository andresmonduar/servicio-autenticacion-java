package com.antobano.auth;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Lógica de negocio del registro y del inicio de sesión.
 *
 * <p>Las contraseñas se protegen con PBKDF2 (HMAC-SHA256) y una sal distinta
 * por usuario. La comparación se hace en tiempo constante para no revelar
 * información por el tiempo de respuesta.</p>
 */
public class ServicioAutenticacion {

    /** Resultado posible de un intento de registro. */
    public enum ResultadoRegistro {
        EXITOSO,
        USUARIO_YA_EXISTE
    }

    /** Longitud mínima y máxima permitida para la contraseña. */
    public static final int LONGITUD_MINIMA_CONTRASENA = 6;
    public static final int LONGITUD_MAXIMA_CONTRASENA = 100;

    private static final int ITERACIONES = 120_000;
    private static final int LONGITUD_CLAVE_BITS = 256;
    private static final int LONGITUD_SAL_BYTES = 16;

    /** Usuario: 3 a 50 caracteres entre letras, números y . _ @ - */
    private static final Pattern PATRON_USUARIO = Pattern.compile("^[A-Za-z0-9._@-]{3,50}$");

    private final RepositorioUsuarios repositorio;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Sal y hash falsos: se usan cuando el usuario no existe, para gastar el mismo tiempo. */
    private final byte[] salFalsa = new byte[LONGITUD_SAL_BYTES];

    /**
     * @param repositorio almacén donde se guardan y se buscan los usuarios
     */
    public ServicioAutenticacion(RepositorioUsuarios repositorio) {
        this.repositorio = repositorio;
        aleatorio.nextBytes(salFalsa);
    }

    /**
     * Revisa que el usuario y la contraseña tengan un formato válido.
     *
     * @return un mensaje de error si algo no cumple, o {@code null} si todo está bien
     */
    public String validarCredenciales(String usuario, String contrasena) {
        if (usuario == null || !PATRON_USUARIO.matcher(usuario).matches()) {
            return "El usuario debe tener entre 3 y 50 caracteres (letras, números, punto, guion, guion bajo o @).";
        }
        if (contrasena == null
                || contrasena.length() < LONGITUD_MINIMA_CONTRASENA
                || contrasena.length() > LONGITUD_MAXIMA_CONTRASENA) {
            return "La contraseña debe tener entre " + LONGITUD_MINIMA_CONTRASENA
                    + " y " + LONGITUD_MAXIMA_CONTRASENA + " caracteres.";
        }
        return null;
    }

    /**
     * Registra un usuario nuevo. Las credenciales ya deben haberse validado.
     *
     * @return {@link ResultadoRegistro#EXITOSO} o {@link ResultadoRegistro#USUARIO_YA_EXISTE}
     * @throws IOException si no se puede guardar el usuario
     */
    public ResultadoRegistro registrar(String usuario, String contrasena) throws IOException {
        String nombre = normalizar(usuario);

        byte[] sal = new byte[LONGITUD_SAL_BYTES];
        aleatorio.nextBytes(sal);
        byte[] hash = calcularHash(contrasena, sal);

        Usuario nuevo = new Usuario(
                nombre,
                Base64.getEncoder().encodeToString(sal),
                Base64.getEncoder().encodeToString(hash));

        return repositorio.agregar(nuevo) ? ResultadoRegistro.EXITOSO : ResultadoRegistro.USUARIO_YA_EXISTE;
    }

    /**
     * Comprueba si el usuario y la contraseña son correctos.
     *
     * @return {@code true} si la autenticación es satisfactoria; {@code false} en cualquier otro caso
     */
    public boolean autenticar(String usuario, String contrasena) {
        if (usuario == null || contrasena == null || contrasena.isEmpty()) {
            return false;
        }
        var encontrado = repositorio.buscar(normalizar(usuario));

        // Si el usuario no existe se calcula igualmente un hash, para que el tiempo
        // de respuesta no delate si el usuario existe o no.
        byte[] sal = encontrado.isPresent()
                ? Base64.getDecoder().decode(encontrado.get().sal())
                : salFalsa;
        byte[] hashCalculado = calcularHash(contrasena, sal);

        if (encontrado.isEmpty()) {
            return false;
        }
        byte[] hashGuardado = Base64.getDecoder().decode(encontrado.get().hash());
        return MessageDigest.isEqual(hashGuardado, hashCalculado);
    }

    /** Los nombres de usuario no distinguen mayúsculas de minúsculas. */
    private String normalizar(String usuario) {
        return usuario.trim().toLowerCase(Locale.ROOT);
    }

    /** Aplica PBKDF2 a la contraseña con la sal indicada. */
    private byte[] calcularHash(String contrasena, byte[] sal) {
        PBEKeySpec especificacion = new PBEKeySpec(contrasena.toCharArray(), sal, ITERACIONES, LONGITUD_CLAVE_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(especificacion)
                    .getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña.", e);
        } finally {
            especificacion.clearPassword();
        }
    }
}
