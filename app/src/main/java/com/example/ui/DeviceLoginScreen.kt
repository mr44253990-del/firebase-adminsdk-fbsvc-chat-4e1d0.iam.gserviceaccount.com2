package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Device Login — Scan QR from web version to log in that browser.
 * Uses native permission launcher (no accompanist) for CI stability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceLoginScreen(
    onBack: () -> Unit,
    onAuthenticated: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser
    val scope = rememberCoroutineScope()
    var scannedPayload by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Point your camera at the QR on your computer") }
    var hasScanned by remember { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Login", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (currentUser == null) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Please sign in on this phone first", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Device Login needs an active account on this device", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
                return@Column
            }

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission && !hasScanned) {
                    QrCameraPreview(
                        onQrDetected = { payload ->
                            if (!hasScanned) {
                                hasScanned = true
                                scannedPayload = payload
                                statusText = "QR detected — authenticating…"
                                vibrate(context)
                                scope.launch {
                                    isAuthenticating = true
                                    authenticateAndLinkQr(context, payload, currentUser.uid) { ok, msg ->
                                        if (ok) {
                                            statusText = "Authenticated! Web is signing in…"
                                            vibrateSuccess(context)
                                            scope.launch {
                                                val firestore = FirebaseFirestore.getInstance()
                                                val docRef = firestore.collection("web_qr_sessions").document(extractQrId(payload))
                                                repeat(20) {
                                                    kotlinx.coroutines.delay(500)
                                                    try {
                                                        val snap = docRef.get().await()
                                                        if (snap.getString("status") == "authenticated") {
                                                            statusText = "Done — your computer is now logged in!"
                                                            onAuthenticated(payload)
                                                            return@launch
                                                        }
                                                    } catch (_: Exception) {}
                                                }
                                            }
                                        } else {
                                            statusText = msg ?: "Authentication failed"
                                            hasScanned = false
                                        }
                                        isAuthenticating = false
                                    }
                                }
                            }
                        }
                    )
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(220.dp).border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp)))
                    }
                } else if (!hasCameraPermission) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
                        Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(56.dp), tint = Color.White)
                        Text("Camera permission needed", color = Color.White, fontWeight = FontWeight.Bold)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera") }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isAuthenticating) CircularProgressIndicator(color = Color.White)
                        else Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(56.dp), tint = Color(0xFF4CAF50))
                        Text(statusText, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                        if (!isAuthenticating) {
                            Button(onClick = { hasScanned = false; scannedPayload = null; statusText = "Point your camera at the QR on your computer" }) {
                                Text("Scan again")
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Computer, null, tint = MaterialTheme.colorScheme.primary)
                        Text("How to log in on your computer", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Text("1. Open Convo Chat Web on your computer\n2. Click “QR” or wait for QR to appear\n3. Scan it here and confirm with your password/biometric\n4. Your account logs in automatically on the web", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                    Text("QR changes every 30 seconds for security", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            Text(statusText, modifier = Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

private fun extractQrId(payload: String): String {
    val trimmed = payload.trim()
    if (trimmed.contains("qrId=")) return trimmed.substringAfter("qrId=").substringBefore("&").substringBefore("#")
    if (trimmed.contains("://")) return trimmed.substringAfterLast("/").substringBefore("?").trim()
    return trimmed.take(64)
}

private suspend fun authenticateAndLinkQr(
    context: Context,
    qrPayload: String,
    uid: String,
    callback: (Boolean, String?) -> Unit
) {
    try {
        val qrId = extractQrId(qrPayload)
        if (qrId.length < 8) { callback(false, "Invalid QR code"); return }
        val firestore = FirebaseFirestore.getInstance()
        val docRef = firestore.collection("web_qr_sessions").document(qrId)
        val snap = docRef.get().await()
        if (!snap.exists()) { callback(false, "QR expired — please refresh on web"); return }
        val status = snap.getString("status") ?: "pending"
        if (status == "expired") { callback(false, "QR expired — please refresh on web"); return }
        docRef.update(
            mapOf(
                "scannedBy" to uid,
                "scannedAt" to System.currentTimeMillis(),
                "status" to "scanned",
                "deviceName" to Build.MODEL
            )
        ).await()
        callback(true, null)
    } catch (e: Exception) {
        callback(false, e.message ?: "Authentication failed")
    }
}

@Composable
private fun QrCameraPreview(onQrDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
            startCamera(ctx, lifecycleOwner, previewView, onQrDetected)
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun startCamera(context: Context, lifecycleOwner: LifecycleOwner, previewView: PreviewView, onQrDetected: (String) -> Unit) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        val scanner = BarcodeScanning.getClient(options)
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var lastScanTime = 0L
        analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
            val now = System.currentTimeMillis()
            if (now - lastScanTime < 1200) { imageProxy.close(); return@setAnalyzer }
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (b in barcodes) {
                            val raw = b.rawValue ?: continue
                            if (raw.isNotBlank()) {
                                lastScanTime = now
                                onQrDetected(raw)
                                break
                            }
                        }
                    }
                    .addOnCompleteListener { imageProxy.close() }
            } else imageProxy.close()
        }
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (e: Exception) {
            Toast.makeText(context, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }, ContextCompat.getMainExecutor(context))
}

private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") vibrator.vibrate(80)
    } catch (_: Exception) {}
}
private fun vibrateSuccess(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 40, 60), -1))
        else @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0, 60, 40, 60), -1)
    } catch (_: Exception) {}
}
