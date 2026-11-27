package com.example.verificationfacial

/** Contrato para guardar, leer y borrar los vectores de un usuario (uno por pose). */
interface RostroRepository {
    fun guardar(pose: Pose, vector: FloatArray)
    fun cargar(pose: Pose): FloatArray?
    /** True solo si están registradas las tres poses. */
    fun hayRegistro(): Boolean
    fun borrar()
}