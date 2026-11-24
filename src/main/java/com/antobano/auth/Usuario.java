package com.antobano.auth;

/**
 * Modelo de datos de un usuario registrado en el servicio.
 *
 * <p>Nunca se guarda la contraseña en texto plano: solo se almacena la sal
 * aleatoria y el hash que resulta de aplicar PBKDF2 a la contraseña.</p>
 *
 * @param nombreUsuario nombre de usuario normalizado (en minúsculas)
 * @param sal           sal aleatoria codificada en Base64
 * @param hash          hash de la contraseña codificado en Base64
 */
public record Usuario(String nombreUsuario, String sal, String hash) {
}
