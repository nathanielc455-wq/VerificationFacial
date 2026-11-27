package com.example.verificationfacial

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class EmbeddingExtractor(context: Context) {

    private val interpreter: Interpreter

    companion object {
        private const val TAM = 112       // lado de la imagen que pide el modelo
        private const val LOTE = 2        // el modelo recibe 2 imágenes a la vez
        private const val DIM = 192       // números por vector
    }

    init {
        interpreter = Interpreter(cargarModelo(context, "modelo_rostro.tflite"))

        val entrada = interpreter.getInputTensor(0)
        val salida = interpreter.getOutputTensor(0)
        Log.d("Modelo", "Entrada: forma=${entrada.shape().toList()} tipo=${entrada.dataType()}")
        Log.d("Modelo", "Salida: forma=${salida.shape().toList()} tipo=${salida.dataType()}")
    }

    private fun cargarModelo(context: Context, nombre: String): MappedByteBuffer {
        val fd = context.assets.openFd(nombre)
        FileInputStream(fd.fileDescriptor).use { stream ->
            return stream.channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    /** Recibe el recorte del rostro y devuelve su vector de 192 números, normalizado. */
    fun extraer(rostro: Bitmap): FloatArray {
        val redimensionada = Bitmap.createScaledBitmap(rostro, TAM, TAM, true)
        val pixeles = IntArray(TAM * TAM)
        redimensionada.getPixels(pixeles, 0, TAM, 0, 0, TAM, TAM)

        // Forma [2, 112, 112, 3]: se repite la misma imagen en las dos posiciones
        val entrada = Array(LOTE) { Array(TAM) { Array(TAM) { FloatArray(3) } } }
        for (i in pixeles.indices) {
            val p = pixeles[i]
            val y = i / TAM
            val x = i % TAM
            val r = (((p shr 16) and 0xFF) - 128f) / 128f
            val g = (((p shr 8) and 0xFF) - 128f) / 128f
            val b = ((p and 0xFF) - 128f) / 128f
            for (lote in 0 until LOTE) {
                entrada[lote][y][x][0] = r
                entrada[lote][y][x][1] = g
                entrada[lote][y][x][2] = b
            }
        }

        val salida = Array(LOTE) { FloatArray(DIM) }
        interpreter.run(entrada, salida)

        return normalizar(salida[0])
    }

    private fun normalizar(v: FloatArray): FloatArray {
        var suma = 0f
        for (x in v) suma += x * x
        val norma = sqrt(suma)
        return if (norma == 0f) v else FloatArray(v.size) { v[it] / norma }
    }

    fun cerrar() {
        interpreter.close()
    }
}