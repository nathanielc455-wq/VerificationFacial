package com.example.verificationfacial

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsuario: EditText
    private lateinit var etClave: EditText
    private lateinit var tvMensaje: TextView
    private lateinit var auth: AuthService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsuario = findViewById(R.id.etUsuario)
        etClave = findViewById(R.id.etClave)
        tvMensaje = findViewById(R.id.tvMensaje)
        auth = AuthService(UserStorage(this))

        findViewById<Button>(R.id.btnIngresar).setOnClickListener { ingresar() }
        findViewById<Button>(R.id.btnCrear).setOnClickListener { crearCuenta() }
    }

    private fun ingresar() {
        val clave = etClave.text.toString().toCharArray()
        val resultado = auth.ingresar(etUsuario.text.toString(), clave)
        clave.fill('\u0000')
        procesar(resultado)
    }

    private fun crearCuenta() {
        val clave = etClave.text.toString().toCharArray()
        val resultado = auth.registrar(etUsuario.text.toString(), clave)
        clave.fill('\u0000')
        procesar(resultado)
    }

    private fun procesar(resultado: ResultadoAuth) {
        if (resultado == ResultadoAuth.OK) {
            val usuario = auth.normalizar(etUsuario.text.toString())
            val intent = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_USUARIO, usuario)
            startActivity(intent)
            finish()
        } else {
            tvMensaje.text = resultado.mensaje
        }
    }
}