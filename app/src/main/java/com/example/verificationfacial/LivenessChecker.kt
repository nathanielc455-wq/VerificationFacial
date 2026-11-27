package com.example.verificationfacial

enum class ResultadoLiveness { INACTIVO, EN_CURSO, SUPERADO, TIEMPO_AGOTADO }

class LivenessChecker(
    private val tiempoMaxMs: Long = 5000,
    private val umbralAbierto: Float = 0.70f,
    private val umbralCerrado: Float = 0.30f
) {
    private enum class Fase { ESPERANDO_ABIERTOS, ESPERANDO_CERRADOS, ESPERANDO_REABIERTOS }

    private var activo = false
    private var fase = Fase.ESPERANDO_ABIERTOS
    private var inicioMs = 0L

    fun iniciar() {
        activo = true
        fase = Fase.ESPERANDO_ABIERTOS
        inicioMs = System.currentTimeMillis()
    }

    fun cancelar() {
        activo = false
    }

    fun estaActivo(): Boolean = activo

    /** Recibe la probabilidad media de ojos abiertos (o -1 si no hay rostro). */
    fun procesar(probOjosAbiertos: Float): ResultadoLiveness {
        if (!activo) return ResultadoLiveness.INACTIVO

        if (System.currentTimeMillis() - inicioMs > tiempoMaxMs) {
            activo = false
            return ResultadoLiveness.TIEMPO_AGOTADO
        }
        if (probOjosAbiertos < 0f) return ResultadoLiveness.EN_CURSO

        when (fase) {
            Fase.ESPERANDO_ABIERTOS ->
                if (probOjosAbiertos > umbralAbierto) fase = Fase.ESPERANDO_CERRADOS
            Fase.ESPERANDO_CERRADOS ->
                if (probOjosAbiertos < umbralCerrado) fase = Fase.ESPERANDO_REABIERTOS
            Fase.ESPERANDO_REABIERTOS ->
                if (probOjosAbiertos > umbralAbierto) {
                    activo = false
                    return ResultadoLiveness.SUPERADO
                }
        }
        return ResultadoLiveness.EN_CURSO
    }
}