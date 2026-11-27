package com.example.verificationfacial

import android.graphics.Bitmap
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Corrección de gamma adaptativa: si el rostro está muy oscuro o muy claro,
 * acerca su brillo a un valor medio. Si ya está bien iluminado, no toca nada.
 */
class MejoradorImagen(private val analizador: AnalizadorAmbiente) {

    private val objetivo = 125f

    fun mejorar(rostro: Bitmap): Bitmap {
        val brillo = analizador.brillo(rostro)
        if (brillo in 90f..170f) return rostro

        val b = brillo.coerceIn(20f, 235f)
        val gamma = (ln(objetivo / 255f) / ln(b / 255f)).coerceIn(0.5f, 2.0f)
        val tabla = IntArray(256) {
            (255.0 * (it / 255.0).pow(gamma.toDouble())).roundToInt().coerceIn(0, 255)
        }

        val copia = rostro.copy(Bitmap.Config.ARGB_8888, true) ?: return rostro
        val ancho = copia.width
        val alto = copia.height
        val pixeles = IntArray(ancho * alto)
        copia.getPixels(pixeles, 0, ancho, 0, 0, ancho, alto)

        for (i in pixeles.indices) {
            val p = pixeles[i]
            val rojo = tabla[(p shr 16) and 0xFF]
            val verde = tabla[(p shr 8) and 0xFF]
            val azul = tabla[p and 0xFF]
            pixeles[i] = (0xFF shl 24) or (rojo shl 16) or (verde shl 8) or azul
        }
        copia.setPixels(pixeles, 0, ancho, 0, 0, ancho, alto)
        return copia
    }
}