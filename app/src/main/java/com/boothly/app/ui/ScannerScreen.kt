package com.boothly.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.boothly.app.data.Repo
import com.boothly.app.scan.DocDetector
import com.boothly.app.util.Images
import com.boothly.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.abs

/** Says when the detected card has stayed still long enough to take the picture automatically. */
private class StableTracker {
    private var last: List<PointF>? = null
    private var count = 0
    fun update(q: List<PointF>?): Boolean {
        if (q == null) {
            last = null; count = 0; return false
        }
        val prev = last
        last = q
        count = if (prev != null && q.indices.all { abs(q[it].x - prev[it].x) < 0.02f && abs(q[it].y - prev[it].y) < 0.02f }) count + 1 else 0
        return count >= 7
    }
    fun reset() {
        last = null; count = 0
    }
}

/**
 * In-app camera.
 *  mode = "card":  live edge detection, auto-capture, crop & enhance (FairScan-style)
 *  mode = "photo": plain photos for products, several in a row
 */
@Composable
fun ScannerScreen(mode: String, onDone: (List<String>) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val cardMode = mode == "card"
    val main = remember { Handler(Looper.getMainLooper()) }

    var hasPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPerm = ok
        if (!ok) toast(ctx, "Camera permission is needed to scan")
    }
    LaunchedEffect(Unit) { if (!hasPerm) permLauncher.launch(Manifest.permission.CAMERA) }

    val captured = remember { mutableStateListOf<String>() }
    var quad by remember { mutableStateOf<List<PointF>?>(null) }
    var frameAspect by remember { mutableFloatStateOf(0.75f) }
    var busy by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(cardMode) }
    var review by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val tracker = remember { StableTracker() }

    val selector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(selector)
            .build()
    }
    val analysis = remember {
        ImageAnalysis.Builder()
            .setResolutionSelector(selector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysis.clearAnalyzer()
            analyzerExecutor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    fun finish() {
        if (captured.isEmpty()) onCancel() else onDone(captured.toList())
    }
    BackHandler {
        if (review != null) review = null else finish()
    }

    fun capture() {
        if (busy) return
        busy = true
        tracker.reset()
        val liveQuad = quad
        imageCapture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotation = image.imageInfo.rotationDegrees
                val raw: Bitmap? = try {
                    image.toBitmap()
                } catch (_: Throwable) {
                    null
                } finally {
                    image.close()
                }
                if (raw == null) {
                    busy = false
                    toast(ctx, "Could not take picture")
                    return
                }
                scope.launch {
                    val name = withContext(Dispatchers.Default) {
                        try {
                            processShot(raw, rotation, cardMode, liveQuad)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    busy = false
                    if (name == null) {
                        toast(ctx, "Could not save picture")
                    } else {
                        captured.add(name)
                        if (cardMode) review = name
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                toast(ctx, "Camera error: ${exception.message}")
            }
        })
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPerm) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c ->
                    val pv = PreviewView(c).apply {
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                    if (cardMode) {
                        var lastRun = 0L
                        analysis.setAnalyzer(analyzerExecutor) { img ->
                            try {
                                val now = System.currentTimeMillis()
                                if (now - lastRun >= 120) {
                                    lastRun = now
                                    val small = DocDetector.prepare(img.toBitmap(), img.imageInfo.rotationDegrees, 480)
                                    val q = DocDetector.detect(small)
                                    val aspect = small.width.toFloat() / small.height
                                    main.post {
                                        quad = q
                                        frameAspect = aspect
                                        if (tracker.update(q) && auto && !busy && review == null) capture()
                                    }
                                }
                            } catch (_: Throwable) {
                            } finally {
                                img.close()
                            }
                        }
                    }
                    val providerFuture = ProcessCameraProvider.getInstance(c)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().setResolutionSelector(selector).build()
                            preview.setSurfaceProvider(pv.surfaceProvider)
                            provider.unbindAll()
                            camera = if (cardMode) {
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
                            } else {
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            }
                        } catch (e: Exception) {
                            toast(c, "Camera not available: ${e.message}")
                        }
                    }, ContextCompat.getMainExecutor(c))
                    pv
                },
            )

            // Detected card outline
            if (cardMode) {
                Canvas(Modifier.fillMaxSize()) {
                    val q = quad ?: return@Canvas
                    val cw = size.width
                    val ch = size.height
                    val imgW: Float
                    val imgH: Float
                    if (cw / ch > frameAspect) {
                        imgH = ch; imgW = ch * frameAspect
                    } else {
                        imgW = cw; imgH = cw / frameAspect
                    }
                    val ox = (cw - imgW) / 2f
                    val oy = (ch - imgH) / 2f
                    val path = Path()
                    q.forEachIndexed { i, p ->
                        val x = ox + p.x * imgW
                        val y = oy + p.y * imgH
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, Color(0x444FC3F7))
                    drawPath(path, Color(0xFF4FC3F7), style = Stroke(width = 4.dp.toPx()))
                }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Boothly needs the camera to scan cards and products.", color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { finish() }) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White) }
            Text(
                if (cardMode) "Scan business card" else "Product photos",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (cardMode) {
                TextButton(onClick = { auto = !auto; tracker.reset() }) {
                    Text(if (auto) "AUTO ON" else "AUTO OFF", color = if (auto) Color(0xFF4FC3F7) else Color.White)
                }
            }
            IconButton(onClick = { torch = !torch }) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, contentDescription = "Flash", tint = Color.White)
            }
        }

        // Bottom bar
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0x88000000))
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    !cardMode -> "Take one or more photos, then tap Done"
                    quad != null -> if (auto) "Card found – hold still…" else "Card found – tap the button"
                    else -> "Put the card on a darker surface and fill the screen"
                },
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                    captured.lastOrNull()?.let { last ->
                        AsyncImage(
                            model = Repo.photoFile(last),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                .clickable { if (cardMode) review = last },
                        )
                    }
                }
                // Shutter
                Box(
                    Modifier
                        .size(76.dp)
                        .border(4.dp, Color.White, CircleShape)
                        .padding(6.dp)
                        .background(if (busy) Color.Gray else Color.White, CircleShape)
                        .clickable(enabled = !busy && hasPerm) { capture() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(32.dp), color = Color.Black)
                }
                Box(Modifier.width(90.dp), contentAlignment = Alignment.Center) {
                    if (captured.isNotEmpty()) {
                        Button(onClick = { finish() }, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                            Text("Done (${captured.size})")
                        }
                    }
                }
            }
        }

        // Review of the cropped card
        val r = review
        if (r != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Check the scan", color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                AsyncImage(
                    model = Repo.photoFile(r),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            captured.remove(r)
                            Repo.photoFile(r).delete()
                            review = null
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Retake", color = Color.White) }
                    OutlinedButton(onClick = { review = null }, modifier = Modifier.weight(1f)) {
                        Text("Scan back", color = Color.White)
                    }
                    Button(onClick = { finish() }, modifier = Modifier.weight(1f)) { Text("Use it") }
                }
            }
        }
    }
}

/** Rotate, crop to the card (if found), enhance, save. Returns the stored file name. */
private fun processShot(raw: Bitmap, rotation: Int, cardMode: Boolean, liveQuad: List<PointF>?): String? {
    val full = DocDetector.prepare(raw, rotation, 3000)
    if (full !== raw) raw.recycle()
    val result: Bitmap = if (cardMode) {
        val small = DocDetector.prepare(full, 0, 900)
        val q = DocDetector.detect(small) ?: liveQuad
        if (small !== full) small.recycle()
        if (q != null) DocDetector.warp(full, q) else DocDetector.enhance(full)
    } else {
        full
    }
    val name = Images.saveBitmap(result)
    if (result !== full) result.recycle()
    full.recycle()
    return name
}
