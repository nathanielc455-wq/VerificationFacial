package com.example.verificationfacial

import kotlin.math.sqrt

class FaceComparator(private val umbral: Float = 0.60f) {

    /** Similitud coseno entre dos vectores: 1 = idénticos, 0 = sin relación. */
    fun similitud(a: FloatArray, b: FloatArray): Float {
        var producto = 0f
        var normaA = 0f
        var normaB = 0f
        for (i in a.indices) {
            producto += a[i] * b[i]
            normaA += a[i] * a[i]
            normaB += b[i] * b[i]
        }
        val denominador = sqrt(normaA) * sqrt(normaB)
        return if (denominador == 0f) 0f else producto / denominador
    }

    fun esMismaPersona(similitud: Float): Boolean = similitud >= umbral

    fun umbralActual(): Float = umbral
}