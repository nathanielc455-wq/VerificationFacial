package com.example.verificationfacial

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Convierte claves en hashes con sal (PBKDF2). La clave original nunca se guarda. */
object PasswordHasher {

    private const val ITERACIONES = 100_000
    private const val LONGITUD_BITS = 256

    fun nuevaSal(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(clave: CharArray, sal: ByteArray): ByteArray {
        val spec = PBEKeySpec(clave, sal, ITERACIONES, LONGITUD_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun coincide(clave: CharArray, sal: ByteArray, esperado: ByteArray): Boolean =
        MessageDigest.isEqual(hash(clave, sal), esperado)
}