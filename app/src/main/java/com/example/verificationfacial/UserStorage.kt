package com.example.verificationfacial

import android.content.Context
import android.util.Base64

/** Guarda por cada usuario: sal + hash de la clave, en el espacio privado de la app. */
class UserStorage(context: Context) : UsuarioRepository {

    private val prefs = context.getSharedPreferences("usuarios_prefs", Context.MODE_PRIVATE)

    override fun existe(usuario: String): Boolean = prefs.contains(clave(usuario))

    override fun crear(usuario: String, clave: CharArray) {
        val sal = PasswordHasher.nuevaSal()
        val hash = PasswordHasher.hash(clave, sal)
        val registro = codificar(sal) + ":" + codificar(hash)
        prefs.edit().putString(clave(usuario), registro).apply()
    }

    override fun validar(usuario: String, clave: CharArray): Boolean {
        val registro = prefs.getString(clave(usuario), null) ?: return false
        val partes = registro.split(":")
        if (partes.size != 2) return false
        val sal = decodificar(partes[0])
        val esperado = decodificar(partes[1])
        return PasswordHasher.coincide(clave, sal, esperado)
    }

    private fun clave(usuario: String) = "usuario_$usuario"

    private fun codificar(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodificar(texto: String): ByteArray = Base64.decode(texto, Base64.NO_WRAP)
}