package com.example.verificationfacial

enum class ResultadoAuth(val mensaje: String) {
    OK("Correcto"),
    USUARIO_INVALIDO("Usuario de 3 a 20 caracteres: letras, números, punto o guion bajo"),
    CLAVE_CORTA("La clave debe tener al menos 6 caracteres"),
    YA_EXISTE("Ese usuario ya existe"),
    CREDENCIALES_INCORRECTAS("Usuario o clave incorrectos")
}

class AuthService(private val repositorio: UsuarioRepository) {

    private val formatoUsuario = Regex("^[a-z0-9_.]{3,20}$")

    fun normalizar(usuario: String): String = usuario.trim().lowercase()

    fun registrar(usuario: String, clave: CharArray): ResultadoAuth {
        val u = normalizar(usuario)
        if (!formatoUsuario.matches(u)) return ResultadoAuth.USUARIO_INVALIDO
        if (clave.size < 6) return ResultadoAuth.CLAVE_CORTA
        if (repositorio.existe(u)) return ResultadoAuth.YA_EXISTE
        repositorio.crear(u, clave)
        return ResultadoAuth.OK
    }

    fun ingresar(usuario: String, clave: CharArray): ResultadoAuth {
        val u = normalizar(usuario)
        if (!formatoUsuario.matches(u)) return ResultadoAuth.CREDENCIALES_INCORRECTAS
        return if (repositorio.validar(u, clave)) ResultadoAuth.OK
        else ResultadoAuth.CREDENCIALES_INCORRECTAS
    }
}