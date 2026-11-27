package com.example.verificationfacial

import android.graphics.Bitmap

data class ResultadoVerificacion(
    val aceptado: Boolean,
    val similitud: Float,
    val milisegundos: Long,
    val pose: Pose
)

class VerificationService(
    private val extractor: EmbeddingExtractor,
    private val comparador: FaceComparator,
    private val repositorio: RostroRepository,
    private val mejorador: MejoradorImagen
) {

    /** Si está activo, el rostro se corrige (gamma) antes de generar el vector. */
    var usarMejora: Boolean = true

    fun hayRegistro(): Boolean = repositorio.hayRegistro()

    /** Recibe una captura por pose y guarda el vector de cada una. */
    fun registrarPatron(capturas: Map<Pose, Bitmap>) {
        for ((pose, rostro) in capturas) {
            repositorio.guardar(pose, extractor.extraer(preparar(rostro)))
        }
    }

    fun borrarRegistro() {
        repositorio.borrar()
    }

    /** Compara contra el patrón de la pose indicada. Devuelve null si no se pudo leer. */
    fun verificar(rostro: Bitmap, pose: Pose): ResultadoVerificacion? {
        val registrado = repositorio.cargar(pose) ?: return null

        val inicio = System.nanoTime()
        val vector = extractor.extraer(preparar(rostro))
        val similitud = comparador.similitud(vector, registrado)
        val milisegundos = (System.nanoTime() - inicio) / 1_000_000

        return ResultadoVerificacion(
            aceptado = comparador.esMismaPersona(similitud),
            similitud = similitud,
            milisegundos = milisegundos,
            pose = pose
        )
    }

    fun umbral(): Float = comparador.umbralActual()

    private fun preparar(rostro: Bitmap): Bitmap =
        if (usarMejora) mejorador.mejorar(rostro) else rostro
}