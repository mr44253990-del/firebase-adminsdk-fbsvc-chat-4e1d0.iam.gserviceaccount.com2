package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.User
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrContactScreen(
    user: User?,
    onBack: () -> Unit,
    onProfileIdScanned: (String) -> Unit
) {
    var scannerMode by remember { mutableStateOf(false) }
    var manualId by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    // The personal QR is generated only on request, so it is never shown by accident.
    var qrRequested by remember { mutableStateOf(false) }
    // Use a real HTTPS URL in the QR so any camera/scanner can read it. The app
    // also accepts the convochat:// equivalent through MainActivity's deep-link filter.
    val effectiveUid = user?.uid?.takeIf { it.isNotBlank() } ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val payload = effectiveUid.takeIf { it.isNotBlank() }?.let { uid ->
        "https://solitary-hill-dcdc.mr44253990.workers.dev/profile?uid=${Uri.encode(uid)}"
    }.orEmpty()
    val qrBitmap = remember(payload, qrRequested) { if (qrRequested) payload.takeIf { it.isNotBlank() }?.let { createQrBitmap(it, 720) } else null }
    val context = LocalContext.current
    // Scan a QR from a saved screenshot/photo.
    val galleryScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                scanner.process(InputImage.fromFilePath(context, uri))
                    .addOnSuccessListener { codes ->
                        val id = codes.firstNotNullOfOrNull { extractProfileId(it.rawValue.orEmpty()) }
                        if (id != null) onProfileIdScanned(id)
                        else Toast.makeText(context, "No Convo profile QR found in that image", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { Toast.makeText(context, "Could not read that image", Toast.LENGTH_SHORT).show() }
            }.onFailure { Toast.makeText(context, "Could not open that image", Toast.LENGTH_SHORT).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR code") },
                navigationIcon = { IconButton(onClick = { if (scannerMode) scannerMode = false else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        if (scannerMode) {
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 10.dp)) {
                QrScannerView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onScanned = { id -> scannerMode = false; onProfileIdScanned(id) },
                    onClose = { scannerMode = false }
                )
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Scan first
            Card(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f))
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(78.dp).clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF6C63FF), androidx.compose.ui.graphics.Color(0xFF00B4D8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(42.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Scan a profile QR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("Point your camera at a Convo QR code. The profile opens automatically.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { scannerMode = true }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Open scanner", fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { galleryScanLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Scan from a photo")
                    }
                }
            }

            // 2. My QR, generated only when asked
            Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode2, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("My QR code", fontWeight = FontWeight.ExtraBold)
                            Text(if (qrRequested) "Anyone who scans this can open your profile" else "Hidden until you generate it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (qrRequested) TextButton(onClick = { qrRequested = false }) { Text("Hide") }
                    }
                    AnimatedVisibility(visible = !qrRequested) {
                        Button(
                            onClick = { qrRequested = true },
                            enabled = payload.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Generate my QR")
                        }
                    }
                    AnimatedVisibility(visible = qrRequested && qrBitmap != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                            qrBitmap?.let { bitmap ->
                                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), modifier = Modifier.size(240.dp)) {
                                    Image(bitmap.asImageBitmap(), contentDescription = "Your Convo profile QR code", Modifier.fillMaxSize().padding(16.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(user?.name?.takeIf { it.isNotBlank() } ?: "Convo user", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            user?.username?.takeIf { it.isNotBlank() }?.let { Text("@$it", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Convo profile link", payload))
                                        Toast.makeText(context, "Profile link copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Copy link") }
                                Button(
                                    onClick = {
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${user?.name?.takeIf { it.isNotBlank() } ?: "Convo user"} on Convo Chat\n$payload")
                                        }, "Share profile link"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Share") }
                            }
                        }
                    }
                }
            }

            // 3. Manual entry (rarely needed)
            TextButton(onClick = { showManual = !showManual }) { Text(if (showManual) "Hide profile ID entry" else "Have a profile ID instead?") }
            AnimatedVisibility(visible = showManual) {
                Column(Modifier.fillMaxWidth()) {
                    OutlinedTextField(manualId, { manualId = it }, Modifier.fillMaxWidth(), label = { Text("Paste profile ID or link") }, singleLine = true, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { (extractProfileId(manualId) ?: manualId.trim().takeIf { it.isNotBlank() })?.let(onProfileIdScanned) },
                        enabled = manualId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open profile") }
                }
            }
        }
    }
}

@Composable
private fun QrScannerView(modifier: Modifier, onScanned: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var scanDelivered by remember { mutableStateOf(false) }
    val boundProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    LaunchedEffect(Unit) { if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    Box(modifier.clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.BottomCenter) {
        if (cameraGranted) {
            val executor = remember { Executors.newSingleThreadExecutor() }
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        boundProvider.value = provider
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { useCase ->
                            useCase.setAnalyzer(executor) { proxy ->
                                val image = proxy.image
                                if (image == null) { proxy.close(); return@setAnalyzer }
                                scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees)).addOnSuccessListener { codes ->
                                    val raw = codes.firstOrNull()?.rawValue.orEmpty()
                                    if (!scanDelivered) extractProfileId(raw)?.let { id ->
                                        scanDelivered = true
                                        onScanned(id)
                                    }
                                }.addOnCompleteListener { proxy.close() }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }, modifier = Modifier.fillMaxSize()
            )
            Text("Point the camera at a Convo profile QR", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(18.dp))
        } else {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required to scan a QR code.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow Camera") }
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close scanner", tint = androidx.compose.ui.graphics.Color.White) }
    }
    // Release the camera as soon as the scanner closes (the screen may stay open).
    DisposableEffect(Unit) { onDispose { runCatching { boundProvider.value?.unbindAll() } } }
}

private fun extractProfileId(raw: String): String? {
    val value = raw.trim()
    if (value.startsWith("convochat:profile:")) return value.substringAfterLast(':').takeIf { it.isNotBlank() }
    runCatching {
        val uri = Uri.parse(value)
        val isProfileLink = (uri.scheme.equals("convochat", true) && uri.host.equals("profile", true)) ||
            (uri.pathSegments.any { it.equals("profile", true) } && uri.getQueryParameter("uid") != null)
        if (isProfileLink) return uri.getQueryParameter("uid")?.trim()?.takeIf { it.isNotBlank() }
    }
    return value.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,}")) }
}

internal fun createQrBitmap(content: String, size: Int): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
    }
}
