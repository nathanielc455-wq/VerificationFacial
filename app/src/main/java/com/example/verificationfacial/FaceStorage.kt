package com.example.verificationfacial

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda los vectores de un usuario (uno por pose) cifrados con AES-GCM.
 * La llave vive en Android Keystore y no se puede extraer de la app.
 */
class FaceStorage(context: Context, private val usuario: String) : RostroRepository {

    private val prefs = context.getSharedPreferences("verificacion_prefs", Context.MODE_PRIVATE)

    private fun clave(pose: Pose) = "vector_cifrado_${usuario}_${pose.name}"

    override fun guardar(pose: Pose, vector: FloatArray) {
        val bytes = ByteBuffer.allocate(vector.size * 4).apply {
            asFloatBuffer().put(vector)
        }.array()

        val cipher = Cipher.getInstance(TRANSFORMACION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerLlave())
        val cifrado = cipher.doFinal(bytes)

        val paquete = cipher.iv + cifrado
        prefs.edit().putString(clave(pose), Base64.encodeToString(paquete, Base64.NO_WRAP)).apply()
    }

    override fun cargar(pose: Pose): FloatArray? {
        val texto = prefs.getString(clave(pose), null) ?: return null
        return try {
            val paquete = Base64.decode(texto, Base64.NO_WRAP)
            val iv = paquete.copyOfRange(0, TAM_IV)
            val cifrado = paquete.copyOfRange(TAM_IV, paquete.size)

            val cipher = Cipher.getInstance(TRANSFORMACION)
            cipher.init(Cipher.DECRYPT_MODE, obtenerLlave(), GCMParameterSpec(128, iv))
            val bytes = cipher.doFinal(cifrado)

            val resultado = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes).asFloatBuffer().get(resultado)
            resultado
        } catch (e: Exception) {
            null
        }
    }

    override fun hayRegistro(): Boolean = Pose.values().all { prefs.contains(clave(it)) }

    override fun borrar() {
        val editor = prefs.edit()
        Pose.values().forEach { editor.remove(clave(it)) }
        editor.remove("vector_cifrado_$usuario") // registro antiguo de una sola pose
        editor.apply()
    }

    private fun obtenerLlave(): SecretKey {
        val almacen = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getKey(ALIAS, null) as? SecretKey)?.let { return it }

        val generador = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        generador.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    companion object {
        private const val ALIAS = "llave_rostro"
        private const val TRANSFORMACION = "AES/GCM/NoPadding"
        private const val TAM_IV = 12
    }
}