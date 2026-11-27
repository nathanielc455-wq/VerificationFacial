package com.example.verificationfacial

import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import java.util.concurrent.TimeUnit

/** Ajusta la exposición y pide a la cámara medir y enfocar sobre el rostro. */
class AmbienteController(private val camara: Camera) {

    private val estadoExposicion = camara.cameraInfo.exposureState
    private val rango = estadoExposicion.exposureCompensationRange
    private val soportado = estadoExposicion.isExposureCompensationSupported
    private var indice = estadoExposicion.exposureCompensationIndex

    private var ultimoAjusteMs = 0L
    private var ultimoEnfoqueMs = 0L

    /** Sube la exposición si hay poca luz y la baja si hay demasiada. */
    fun ajustarExposicion(brillo: Float) {
        if (!soportado) return
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoAjusteMs < 800) return

        val nuevo = when {
            brillo < 90f -> indice + 1
            brillo > 175f -> indice - 1
            else -> indice
        }.coerceIn(rango.lower, rango.upper)

        if (nuevo != indice) {
            camara.cameraControl.setExposureCompensationIndex(nuevo)
            Log.d("Ambiente", "Exposición $indice -> $nuevo (brillo ${brillo.toInt()})")
            indice = nuevo
            ultimoAjusteMs = ahora
        }
    }

    /** Pide enfoque y medición de luz en un punto. Muchas cámaras frontales no tienen enfoque. */
    fun medirYEnfocar(punto: MeteringPoint) {
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoEnfoqueMs < 4000) return
        ultimoEnfoqueMs = ahora

        val accion = FocusMeteringAction.Builder(
            punto,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        try {
            camara.cameraControl.startFocusAndMetering(accion)
        } catch (e: Exception) {
            Log.w("Ambiente", "No se pudo enfocar/medir", e)
        }
    }
}