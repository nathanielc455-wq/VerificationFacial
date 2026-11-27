package com.example.verificationfacial

import android.graphics.Bitmap

enum class Ambiente(val mensaje: String) {
    OK("Ambiente adecuado"),
    POCA_LUZ("Poca luz: busca una zona más iluminada"),
    DEMASIADA_LUZ("Demasiada luz: evita el sol directo"),
    CONTRALUZ("Contraluz: no te pongas frente a una ventana o lámpara"),
    BORROSA("Imagen borrosa: mantén quieto el teléfono")
}

/** Mide brillo y nitidez de una imagen y decide si el ambiente es adecuado. */
class AnalizadorAmbiente {

    companion object {
        // Valores de partida: se calibran viendo los números en pantalla
        const val BRILLO_MIN = 70f
        const val BRILLO_MAX = 190f
        const val NITIDEZ_MIN = 12f
        private const val LADO = 96
    }

    /** Brillo medio de un recorte, de 0 (negro) a 255 (blanco). */
    fun brillo(imagen: Bitmap): Float {
        val pixeles = pixelesReducidos(imagen)
        var suma = 0.0
        for (p in pixeles) suma += luminancia(p)
        return (suma / pixeles.size).toFloat()
    }

    /** Nitidez como varianza del laplaciano. Más alto = más nítido. */
    fun nitidez(imagen: Bitmap): Float {
        val pixeles = pixelesReducidos(imagen)
        val gris = FloatArray(LADO * LADO) { luminancia(pixeles[it]).toFloat() }

        var suma = 0.0
        var sumaCuadrados = 0.0
        var n = 0
        for (y in 1 until LADO - 1) {
            for (x in 1 until LADO - 1) {
                val i = y * LADO + x
                val laplaciano = 4 * gris[i] - gris[i - 1] - gris[i + 1] - gris[i - LADO] - gris[i + LADO]
                suma += laplaciano
                sumaCuadrados += laplaciano * laplaciano
                n++
            }
        }
        val media = suma / n
        return (sumaCuadrados / n - media * media).toFloat()
    }

    /**
     * brilloRostro es null si no hay rostro en el cuadro; entonces solo se
     * evalúa la luz general de la escena.
     */
    fun evaluar(brilloRostro: Float?, brilloGlobal: Float, nitidez: Float?): Ambiente {
        if (brilloRostro == null) {
            return when {
                brilloGlobal < BRILLO_MIN -> Ambiente.POCA_LUZ
                brilloGlobal > BRILLO_MAX -> Ambiente.DEMASIADA_LUZ
                else -> Ambiente.OK
            }
        }
        if (brilloRostro < BRILLO_MIN) {
            // Rostro oscuro con fondo claro = contraluz
            return if (brilloGlobal > 140f) Ambiente.CONTRALUZ else Ambiente.POCA_LUZ
        }
        if (brilloRostro > BRILLO_MAX) return Ambiente.DEMASIADA_LUZ
        if (nitidez != null && nitidez < NITIDEZ_MIN) return Ambiente.BORROSA
        return Ambiente.OK
    }

    private fun pixelesReducidos(imagen: Bitmap): IntArray {
        val chica = Bitmap.createScaledBitmap(imagen, LADO, LADO, true)
        val pixeles = IntArray(LADO * LADO)
        chica.getPixels(pixeles, 0, LADO, 0, 0, LADO, LADO)
        return pixeles
    }

    private fun luminancia(pixel: Int): Double {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}