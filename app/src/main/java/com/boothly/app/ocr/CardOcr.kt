package com.boothly.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class OcrResult(val lines: List<String>, val qr: List<String>) {
    val raw: String
        get() = (lines + qr.map { "[QR] $it" }).joinToString("\n")
}

/**
 * Offline card reader:
 *  - Google ML Kit (bundled) for Chinese + English text
 *  - ML Kit barcode scanner for QR codes on the card
 *  - Tesseract for Arabic text
 */
object CardOcr {
    private val mutex = Mutex()
    private val textRecognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }
    private val ARABIC = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")

    suspend fun read(ctx: Context, files: List<File>): OcrResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val lines = mutableListOf<String>()
            val qr = mutableListOf<String>()
            for (f in files) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
                val image = InputImage.fromBitmap(bmp, 0)
                try {
                    val text = textRecognizer.process(image).await()
                    text.textBlocks.forEach { b -> b.lines.forEach { l -> lines.add(l.text.trim()) } }
                } catch (_: Exception) {
                }
                try {
                    barcodeScanner.process(image).await().forEach { c ->
                        val v = c.rawValue
                        if (!v.isNullOrBlank()) qr.add(v)
                    }
                } catch (_: Exception) {
                }
                try {
                    lines.addAll(arabic(ctx, bmp))
                } catch (_: Throwable) {
                }
                bmp.recycle()
            }
            OcrResult(lines.filter { it.isNotBlank() }, qr.distinct())
        }
    }

    private fun arabic(ctx: Context, bmp: Bitmap): List<String> {
        val base = File(ctx.filesDir, "tesseract")
        val dataDir = File(base, "tessdata").apply { mkdirs() }
        val trained = File(dataDir, "ara.traineddata")
        if (!trained.exists()) {
            val tmp = File(dataDir, "ara.tmp")
            try {
                ctx.assets.open("tessdata/ara.traineddata").use { input ->
                    FileOutputStream(tmp).use { input.copyTo(it) }
                }
            } catch (_: Exception) {
                tmp.delete()
                return emptyList()
            }
            if (!tmp.renameTo(trained)) return emptyList()
        }
        val tess = TessBaseAPI()
        try {
            if (!tess.init(base.absolutePath, "ara")) return emptyList()
            tess.setImage(bmp)
            val text: String = tess.getUTF8Text() ?: return emptyList()
            if (tess.meanConfidence() < 55) return emptyList()
            return text.lines().map { it.trim() }.filter { line ->
                val ar = ARABIC.findAll(line).count()
                val letters = line.count { it.isLetter() }
                ar >= 3 && ar * 2 >= letters
            }
        } finally {
            tess.recycle()
        }
    }
}
