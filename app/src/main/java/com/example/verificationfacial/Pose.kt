package com.example.verificationfacial

import kotlin.math.abs

enum class Pose(val etiqueta: String, val instruccion: String) {
    FRENTE("frente", "Mira de frente a la cámara"),
    PERFIL_IZQUIERDO("perfil izquierdo", "Gira la cabeza hacia tu izquierda"),
    PERFIL_DERECHO("perfil derecho", "Gira la cabeza hacia tu derecha")
}

/**
 * Clasifica la pose a partir del giro horizontal de la cabeza (yaw) que da ML Kit.
 *
 * Si al girar a TU izquierda la app detecta "perfil derecho" (o al revés),
 * cambia SIGNO_IZQUIERDA de 1f a -1f.
 */
class PoseClassifier {

    private val SIGNO_IZQUIERDA = -1f

    /** Para verificar: siempre devuelve la pose más cercana. */
    fun clasificar(yaw: Float): Pose {
        val giro = yaw * SIGNO_IZQUIERDA
        return when {
            abs(giro) < 18f -> Pose.FRENTE
            giro > 0 -> Pose.PERFIL_IZQUIERDO
            else -> Pose.PERFIL_DERECHO
        }
    }

    /** Para el registro: exige un ángulo más estricto. */
    fun cumple(pose: Pose, yaw: Float): Boolean {
        val giro = yaw * SIGNO_IZQUIERDA
        return when (pose) {
            Pose.FRENTE -> abs(giro) <= 10f
            Pose.PERFIL_IZQUIERDO -> giro in 25f..50f
            Pose.PERFIL_DERECHO -> giro in -50f..-25f
        }
    }
}