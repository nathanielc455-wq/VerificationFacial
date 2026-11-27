package com.example.verificationfacial

import android.graphics.Bitmap

enum class EstadoRegistro { INACTIVO, EN_CURSO, POSE_CAPTURADA, COMPLETO, TIEMPO_AGOTADO }

/** Máquina de estados del registro: frente → perfil izquierdo → perfil derecho. */
class RegistroGuiado(
    private val tiempoMaxPorPoseMs: Long = Long.MAX_VALUE,
    private val cuadrosEstables: Int = 8
) {
    private val orden = listOf(Pose.FRENTE, Pose.PERFIL_IZQUIERDO, Pose.PERFIL_DERECHO)
    private val capturas = mutableMapOf<Pose, Bitmap>()

    private var activo = false
    private var indice = 0
    private var estables = 0
    private var inicioPoseMs = 0L

    fun iniciar() {
        capturas.clear()
        indice = 0
        estables = 0
        inicioPoseMs = System.currentTimeMillis()
        activo = true
    }

    fun cancelar() {
        activo = false
        capturas.clear()
    }

    fun estaActivo(): Boolean = activo

    fun poseActual(): Pose? = if (activo) orden[indice] else null

    fun paso(): Int = indice + 1

    fun total(): Int = orden.size

    fun obtenerCapturas(): Map<Pose, Bitmap> = capturas.toMap()

    /** Se llama en cada cuadro. poseCorrecta = la pose pedida se está cumpliendo ahora. */
    fun procesar(poseCorrecta: Boolean, rostro: Bitmap?): EstadoRegistro {
        if (!activo) return EstadoRegistro.INACTIVO

        if (System.currentTimeMillis() - inicioPoseMs > tiempoMaxPorPoseMs) {
            activo = false
            return EstadoRegistro.TIEMPO_AGOTADO
        }

        if (!poseCorrecta || rostro == null) {
            estables = 0
            return EstadoRegistro.EN_CURSO
        }

        estables++
        if (estables < cuadrosEstables) return EstadoRegistro.EN_CURSO

        capturas[orden[indice]] = rostro
        indice++
        estables = 0

        if (indice >= orden.size) {
            activo = false
            return EstadoRegistro.COMPLETO
        }
        inicioPoseMs = System.currentTimeMillis()
        return EstadoRegistro.POSE_CAPTURADA
    }
}