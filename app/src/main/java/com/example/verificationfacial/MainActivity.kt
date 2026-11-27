package com.example.verificationfacial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var ambienteText: TextView
    private lateinit var tvUsuario: TextView
    private lateinit var btnRegistrar: Button
    private lateinit var btnVerificar: Button
    private lateinit var btnBorrar: Button
    private lateinit var btnSalir: Button
    private lateinit var chkLiveness: CheckBox
    private lateinit var chkAsistente: CheckBox

    private lateinit var analysisExecutor: ExecutorService
    private lateinit var faceAnalyzer: FaceAnalyzer
    private lateinit var embeddingExtractor: EmbeddingExtractor
    private lateinit var service: VerificationService

    private val analizadorAmbiente = AnalizadorAmbiente()
    private var controlAmbiente: AmbienteController? = null

    private val liveness = LivenessChecker()
    private val registro = RegistroGuiado()
    private val clasificador = PoseClassifier()
    private var ultimoFrame: FaceFrame? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Se necesita el permiso de cámara", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val usuario = intent.getStringExtra(EXTRA_USUARIO)
        if (usuario == null) {
            volverALogin()
            return
        }

        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        ambienteText = findViewById(R.id.ambienteText)
        tvUsuario = findViewById(R.id.tvUsuario)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        btnVerificar = findViewById(R.id.btnVerificar)
        btnBorrar = findViewById(R.id.btnBorrar)
        btnSalir = findViewById(R.id.btnSalir)
        chkLiveness = findViewById(R.id.chkLiveness)
        chkAsistente = findViewById(R.id.chkAsistente)

        tvUsuario.text = "Usuario: $usuario"

        analysisExecutor = Executors.newSingleThreadExecutor()
        embeddingExtractor = EmbeddingExtractor(this)
        service = VerificationService(
            extractor = embeddingExtractor,
            comparador = FaceComparator(),
            repositorio = FaceStorage(this, usuario),
            mejorador = MejoradorImagen(analizadorAmbiente)
        )
        service.usarMejora = chkAsistente.isChecked
        chkAsistente.setOnCheckedChangeListener { _, marcado -> service.usarMejora = marcado }

        faceAnalyzer = FaceAnalyzer(analizadorAmbiente) { frame -> alRecibirFrame(frame) }

        if (service.hayRegistro()) {
            mostrarResultado("Patrón de rostro registrado", Color.WHITE)
        } else {
            mostrarResultado("Aún no has registrado tu patrón de rostro", Color.WHITE)
        }

        btnRegistrar.setOnClickListener { iniciarRegistro() }
        btnVerificar.setOnClickListener { iniciarVerificacion() }
        btnBorrar.setOnClickListener { confirmarBorrado() }
        btnSalir.setOnClickListener {
            cancelarProcesos()
            volverALogin()
        }

        val tienePermiso = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (tienePermiso) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun volverALogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun cancelarProcesos() {
        registro.cancelar()
        liveness.cancelar()
    }

    // ---------- Cada cuadro de la cámara ----------

    private fun alRecibirFrame(frame: FaceFrame) {
        ultimoFrame = frame
        actualizarAmbiente(frame)

        if (registro.estaActivo()) {
            procesarRegistro(frame)
            return
        }

        val yaw = frame.yaw
        val etiquetaPose = if (frame.estado == FaceStatus.OK && yaw != null) {
            "  |  " + clasificador.clasificar(yaw).etiqueta
        } else {
            ""
        }
        statusText.text = frame.estado.mensaje + etiquetaPose

        procesarLiveness(frame.ojosAbiertos)
    }

    // ---------- Asistente de ambiente ----------

    private fun ambienteAceptable(frame: FaceFrame): Boolean =
        !chkAsistente.isChecked || frame.ambiente == Ambiente.OK

    private fun actualizarAmbiente(frame: FaceFrame) {
        val nitidez = frame.nitidez?.let { "  ·  nitidez ${it.toInt()}" } ?: ""
        ambienteText.text = "${frame.ambiente.mensaje}  ·  brillo ${frame.brillo.toInt()}$nitidez"
        ambienteText.setTextColor(
            if (frame.ambiente == Ambiente.OK) Color.GREEN else Color.YELLOW
        )

        if (!chkAsistente.isChecked) return
        val control = controlAmbiente ?: return

        control.ajustarExposicion(frame.brillo)
        if (frame.estado != FaceStatus.SIN_ROSTRO) {
            val punto = previewView.meteringPointFactory.createPoint(
                previewView.width / 2f, previewView.height / 2f
            )
            control.medirYEnfocar(punto)
        }
    }

    // ---------- Registro guiado (frente, perfil izquierdo, perfil derecho) ----------

    private fun iniciarRegistro() {
        liveness.cancelar()
        registro.iniciar()
        mostrarResultado("Registro de patrón: paso 1 de ${registro.total()}", Color.YELLOW)
        statusText.text = registro.poseActual()?.instruccion ?: ""
    }

    private fun procesarRegistro(frame: FaceFrame) {
        val actual = registro.poseActual() ?: return
        val yaw = frame.yaw
        val ambienteOk = ambienteAceptable(frame)
        val poseCorrecta = frame.estado == FaceStatus.OK && yaw != null &&
                clasificador.cumple(actual, yaw) && ambienteOk

        when (registro.procesar(poseCorrecta, frame.rostro)) {
            EstadoRegistro.POSE_CAPTURADA -> {
                mostrarResultado(
                    "Capturado: ${actual.etiqueta}  |  paso ${registro.paso()} de ${registro.total()}",
                    Color.GREEN
                )
                statusText.text = registro.poseActual()?.instruccion ?: ""
            }
            EstadoRegistro.COMPLETO -> {
                service.registrarPatron(registro.obtenerCapturas())
                registro.cancelar() // libera las capturas de memoria
                Log.d("Resultado", "Patrón registrado (frente y ambos perfiles, cifrado)")
                mostrarResultado("Patrón registrado: frente y ambos perfiles", Color.GREEN)
                statusText.text = frame.estado.mensaje
            }
            EstadoRegistro.TIEMPO_AGOTADO -> {
                registro.cancelar()
                mostrarResultado("Registro cancelado: se acabó el tiempo", Color.RED)
                statusText.text = frame.estado.mensaje
            }
            else -> {
                val angulo = yaw?.toInt() ?: 0
                statusText.text = when {
                    frame.estado != FaceStatus.OK -> frame.estado.mensaje
                    !ambienteOk -> frame.ambiente.mensaje
                    else -> "${actual.instruccion}  (ángulo $angulo°)"
                }
            }
        }
    }

    // ---------- Verificación ----------

    private fun iniciarVerificacion() {
        registro.cancelar()
        if (!service.hayRegistro()) {
            mostrarResultado("Primero registra tu patrón de rostro", Color.YELLOW)
            return
        }
        if (chkLiveness.isChecked) {
            liveness.iniciar()
            mostrarResultado("Parpadea ahora...", Color.YELLOW)
        } else {
            ejecutarVerificacion(conLiveness = false)
        }
    }

    private fun procesarLiveness(probOjosAbiertos: Float) {
        when (liveness.procesar(probOjosAbiertos)) {
            ResultadoLiveness.SUPERADO -> ejecutarVerificacion(conLiveness = true)
            ResultadoLiveness.TIEMPO_AGOTADO -> {
                Log.d("Resultado", "RECHAZADO por liveness: no se detectó parpadeo")
                mostrarResultado("RECHAZADO  |  no se detectó parpadeo", Color.RED)
            }
            else -> {}
        }
    }

    private fun ejecutarVerificacion(conLiveness: Boolean) {
        val frame = ultimoFrame
        val rostro = frame?.rostro
        val yaw = frame?.yaw
        if (frame == null || rostro == null || yaw == null) {
            mostrarResultado("Ubica tu rostro hasta ver \"Rostro correcto\"", Color.YELLOW)
            return
        }
        if (!ambienteAceptable(frame)) {
            mostrarResultado(frame.ambiente.mensaje, Color.YELLOW)
            return
        }

        val pose = clasificador.clasificar(yaw)
        val resultado = service.verificar(rostro, pose)
        if (resultado == null) {
            mostrarResultado("No se pudo leer el patrón. Regístralo de nuevo.", Color.YELLOW)
            return
        }

        val texto = if (resultado.aceptado) "ACEPTADO" else "RECHAZADO"
        val detalle = "$texto  |  ${resultado.pose.etiqueta}  |  similitud %.2f  |  ${resultado.milisegundos} ms"
            .format(resultado.similitud)
        Log.d(
            "Resultado",
            "$detalle  |  umbral ${service.umbral()}  |  liveness=$conLiveness" +
                    "  |  asistente=${chkAsistente.isChecked}  |  brillo=${frame.brillo.toInt()}"
        )
        mostrarResultado(detalle, if (resultado.aceptado) Color.GREEN else Color.RED)
    }

    // ---------- Borrado y pantalla ----------

    private fun confirmarBorrado() {
        cancelarProcesos()
        if (!service.hayRegistro()) {
            mostrarResultado("No hay ningún patrón registrado", Color.YELLOW)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Borrar registro")
            .setMessage("Se eliminará tu patrón de rostro guardado en este teléfono. ¿Continuar?")
            .setPositiveButton("Borrar") { _, _ ->
                service.borrarRegistro()
                Log.d("Resultado", "Registro borrado")
                mostrarResultado("Registro borrado", Color.WHITE)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarResultado(texto: String, color: Int) {
        resultText.text = texto
        resultText.setTextColor(color)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analysisExecutor, faceAnalyzer) }

            try {
                provider.unbindAll()
                val camara = provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
                controlAmbiente = AmbienteController(camara)
            } catch (e: Exception) {
                Log.e("Camara", "No se pudo iniciar la cámara", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdown()
        if (::faceAnalyzer.isInitialized) faceAnalyzer.cerrar()
        if (::embeddingExtractor.isInitialized) embeddingExtractor.cerrar()
    }

    companion object {
        const val EXTRA_USUARIO = "usuario"
    }
}