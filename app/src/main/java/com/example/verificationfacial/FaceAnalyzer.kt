package com.example.verificationfacial

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

enum class FaceStatus(val mensaje: String) {
    SIN_ROSTRO("No se detecta ningún rostro"),
    VARIOS("Debe aparecer una sola persona"),
    LEJOS("Acércate un poco"),
    CERCA("Aléjate un poco"),
    DESCENTRADO("Centra tu rostro"),
    OK("Rostro correcto")
}

/** Todo lo que se sabe de un cuadro de la cámara. */
data class FaceFrame(
    val estado: FaceStatus,
    val ojosAbiertos: Float,   // -1 si no hay dato
    val yaw: Float?,           // giro horizontal de la cabeza en grados
    val rostro: Bitmap?,       // recorte del rostro, solo si el estado es OK
    val ambiente: Ambiente,    // resultado del análisis de luz y nitidez
    val brillo: Float,         // brillo del rostro, o de la escena si no hay rostro
    val nitidez: Float?        // solo cuando hay recorte del rostro
)

class FaceAnalyzer(
    private val analizador: AnalizadorAmbiente,
    private val onFrame: (FaceFrame) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val brilloGlobal = brilloGlobal(imageProxy)

        val rotation = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotation)

        // Tamaño de la imagen ya rotada, que es el sistema de coordenadas de ML Kit
        val girada = rotation == 90 || rotation == 270
        val ancho = if (girada) imageProxy.height else imageProxy.width
        val alto = if (girada) imageProxy.width else imageProxy.height

        detector.process(image)
            .addOnSuccessListener { rostros ->
                val estado = evaluar(rostros, ancho, alto)
                val rostro = if (estado == FaceStatus.OK) {
                    recortarRostro(imageProxy, rostros[0].boundingBox, rotation)
                } else {
                    null
                }
                val yaw = if (rostros.size == 1) rostros[0].headEulerAngleY else null

                val brilloRostro = rostro?.let { analizador.brillo(it) }
                val nitidez = rostro?.let { analizador.nitidez(it) }
                val ambiente = analizador.evaluar(brilloRostro, brilloGlobal, nitidez)

                onFrame(
                    FaceFrame(
                        estado, probabilidadOjosAbiertos(rostros), yaw, rostro,
                        ambiente, brilloRostro ?: brilloGlobal, nitidez
                    )
                )
            }
            .addOnFailureListener {
                val ambiente = analizador.evaluar(null, brilloGlobal, null)
                onFrame(
                    FaceFrame(
                        FaceStatus.SIN_ROSTRO, -1f, null, null,
                        ambiente, brilloGlobal, null
                    )
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    /** Brillo medio de toda la escena, leyendo el plano de luminancia (Y) por muestreo. */
    private fun brilloGlobal(imageProxy: ImageProxy): Float {
        return try {
            val plano = imageProxy.planes[0]
            val buffer = plano.buffer
            val filaPaso = plano.rowStride
            val pixelPaso = plano.pixelStride

            var suma = 0L
            var n = 0
            var fila = 0
            while (fila < imageProxy.height) {
                var col = 0
                while (col < imageProxy.width) {
                    suma += buffer.get(fila * filaPaso + col * pixelPaso).toInt() and 0xFF
                    n++
                    col += 16
                }
                fila += 16
            }
            if (n == 0) 128f else suma.toFloat() / n
        } catch (e: Exception) {
            128f
        }
    }

    private fun probabilidadOjosAbiertos(rostros: List<Face>): Float {
        if (rostros.size != 1) return -1f
        val izq = rostros[0].leftEyeOpenProbability
        val der = rostros[0].rightEyeOpenProbability
        if (izq == null || der == null) return -1f
        return (izq + der) / 2f
    }

    private fun evaluar(rostros: List<Face>, ancho: Int, alto: Int): FaceStatus {
        if (rostros.isEmpty()) return FaceStatus.SIN_ROSTRO
        if (rostros.size > 1) return FaceStatus.VARIOS

        val caja = rostros[0].boundingBox
        val proporcion = caja.height().toFloat() / alto

        if (proporcion < 0.30f) return FaceStatus.LEJOS
        if (proporcion > 0.65f) return FaceStatus.CERCA

        val desvioX = abs(caja.centerX() - ancho / 2f) / ancho
        val desvioY = abs(caja.centerY() - alto / 2f) / alto
        if (desvioX > 0.15f || desvioY > 0.15f) return FaceStatus.DESCENTRADO

        return FaceStatus.OK
    }

    private fun recortarRostro(imageProxy: ImageProxy, caja: Rect, rotation: Int): Bitmap? {
        return try {
            val original = imageProxy.toBitmap()
            val matriz = Matrix().apply { postRotate(rotation.toFloat()) }
            val girado = Bitmap.createBitmap(
                original, 0, 0, original.width, original.height, matriz, true
            )

            val izquierda = caja.left.coerceIn(0, girado.width - 1)
            val arriba = caja.top.coerceIn(0, girado.height - 1)
            val ancho = caja.width().coerceAtMost(girado.width - izquierda)
            val alto = caja.height().coerceAtMost(girado.height - arriba)
            if (ancho <= 0 || alto <= 0) return null

            Bitmap.createBitmap(girado, izquierda, arriba, ancho, alto)
        } catch (e: Exception) {
            null
        }
    }

    fun cerrar() {
        detector.close()
    }
}