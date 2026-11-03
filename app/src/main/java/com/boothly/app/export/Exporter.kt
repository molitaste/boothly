package com.boothly.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.boothly.app.data.AppData
import com.boothly.app.data.Product
import com.boothly.app.data.Repo
import com.boothly.app.data.Supplier
import com.boothly.app.data.appDataFromJson
import com.boothly.app.data.toJson
import com.boothly.app.util.Images
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Exporter {
    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_PDF = "application/pdf"
    const val MIME_ZIP = "application/zip"

    private fun stamp() = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    private fun date(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))

    private fun outDir(ctx: Context): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val old = System.currentTimeMillis() - 60L * 60 * 1000
        dir.listFiles()?.forEach { if (it.lastModified() < old) it.delete() }
        return dir
    }

    fun share(ctx: Context, file: File, mime: String) {
        val uri = Images.uriFor(ctx, file)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(i, "Share ${file.name}"))
    }

    fun copyTo(ctx: Context, file: File, target: Uri) {
        ctx.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
    }

    // ---------------------------------------------------------------- Excel

    fun excel(ctx: Context): File {
        val f = File(outDir(ctx), "Boothly_${stamp()}.xlsx")
        XlsxReport.build(f)
        return f
    }

    // ---------------------------------------------------------------- PDF

    fun pdf(ctx: Context): File {
        val f = File(outDir(ctx), "Boothly_report_${stamp()}.pdf")
        PdfReport.build(f)
        return f
    }

    // ---------------------------------------------------------------- Backup

    fun backup(ctx: Context): File {
        val d = Repo.data.value
        val f = File(outDir(ctx), "Boothly_backup_${stamp()}.zip")
        ZipOutputStream(FileOutputStream(f)).use { z ->
            z.putNextEntry(ZipEntry("data.json"))
            z.write(d.toJson().toString(2).toByteArray(Charsets.UTF_8))
            z.closeEntry()
            Repo.allPhotoNames(d).distinct().forEach { name ->
                val pf = Repo.photoFile(name)
                if (pf.exists()) {
                    z.putNextEntry(ZipEntry("photos/$name"))
                    pf.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return f
    }

    /** Restores a backup ZIP (merges with current data). Returns (suppliers, products) or null if invalid. */
    fun restore(ctx: Context, uri: Uri): Pair<Int, Int>? {
        var json: String? = null
        val input = ctx.contentResolver.openInputStream(uri) ?: return null
        ZipInputStream(input).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val n = e.name
                if (n == "data.json") {
                    json = z.readBytes().toString(Charsets.UTF_8)
                } else if (n.startsWith("photos/") && !e.isDirectory) {
                    val safe = File(n).name
                    if (safe.isNotBlank() && !safe.startsWith(".")) {
                        FileOutputStream(Repo.photoFile(safe)).use { z.copyTo(it) }
                    }
                }
                z.closeEntry()
                e = z.nextEntry
            }
        }
        val text = json ?: return null
        val data: AppData = appDataFromJson(JSONObject(text))
        Repo.merge(data)
        return data.suppliers.size to data.products.size
    }
}
