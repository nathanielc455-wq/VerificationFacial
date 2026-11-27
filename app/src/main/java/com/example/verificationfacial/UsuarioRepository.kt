package com.example.verificationfacial

/** Contrato para guardar y validar usuarios. */
interface UsuarioRepository {
    fun existe(usuario: String): Boolean
    fun crear(usuario: String, clave: CharArray)
    fun validar(usuario: String, clave: CharArray): Boolean
}