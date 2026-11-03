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
        val d = Repo.data.value
        val f = File(outDir(ctx), "Boothly_report_${stamp()}.pdf")
        val b = PdfBuilder()

        b.newPage()
        b.y = 200f
        b.text("Boothly", b.huge)
        b.text("Trade fair report", b.title)
        b.y += 12f
        b.text("Created ${date(System.currentTimeMillis())}", b.body)
        b.text("${d.suppliers.size} suppliers · ${d.products.size} products", b.body)

        d.suppliers.sortedBy { it.createdAt }.forEach { s ->
            supplierPage(b, s, d.products.filter { it.supplierId == s.id }.sortedBy { it.createdAt })
        }
        b.finish()
        FileOutputStream(f).use { b.doc.writeTo(it) }
        b.doc.close()
        return f
    }

    private fun supplierPage(b: PdfBuilder, s: Supplier, products: List<Product>) {
        b.newPage()
        b.text(s.displayName, b.title)
        b.y += 6f

        val top = b.y
        val imgW = 220f
        val imgH = 140f
        var imgBottom = top
        s.cardImages.firstOrNull()?.let { name ->
            val h = b.image(Repo.photoFile(name), b.m, top, imgW, imgH)
            imgBottom = top + h
        }
        val info = buildString {
            val who = listOf(s.contactName, s.title).filter { it.isNotBlank() }.joinToString(" – ")
            if (who.isNotBlank()) appendLine(who)
            if (s.booth.isNotBlank()) appendLine("Booth: ${s.booth}")
            s.phones.forEach { appendLine("Tel: $it") }
            s.emails.forEach { appendLine("Email: $it") }
            if (s.whatsapp.isNotBlank()) appendLine("WhatsApp: ${s.whatsapp}")
            if (s.wechat.isNotBlank()) appendLine("WeChat: ${s.wechat}")
            if (s.website.isNotBlank()) appendLine("Web: ${s.website}")
            if (s.address.isNotBlank()) appendLine("Address: ${s.address}")
        }.trim()
        val infoX = if (s.cardImages.isEmpty()) b.m else b.m + imgW + 14f
        val infoW = (b.w - b.m - infoX).toInt()
        val layout = b.layout(info.ifBlank { " " }, b.body, infoW)
        b.drawLayout(layout, infoX, top)
        b.y = maxOf(imgBottom, top + layout.height) + 12f

        if (s.notes.isNotBlank()) {
            b.text("Notes: ${s.notes}", b.small)
            b.y += 6f
        }

        b.ensure(30f)
        b.text("Products (${products.size})", b.bold)
        b.y += 4f

        val gap = 12f
        val colW = (b.w - 2 * b.m - 2 * gap) / 3f
        val photoH = 120f
        products.chunked(3).forEach { row ->
            val texts = row.map { p ->
                val t = buildString {
                    append(p.displayName)
                    if (p.priceLabel.isNotBlank()) append("\n").append(p.priceLabel)
                    if (p.moq.isNotBlank()) append("\nMOQ: ").append(p.moq)
                    if (p.notes.isNotBlank()) append("\n").append(p.notes)
                }
                b.layout(t, b.small, colW.toInt(), maxLines = 6)
            }
            val rowH = photoH + 6f + (texts.maxOfOrNull { it.height } ?: 0) + 14f
            if (b.ensure(rowH)) {
                b.text("${s.displayName} – products (continued)", b.bold)
                b.y += 4f
            }
            val rowTop = b.y
            row.forEachIndexed { i, p ->
                val x = b.m + i * (colW + gap)
                val photo = p.photos.firstOrNull()
                if (photo != null) b.image(Repo.photoFile(photo), x, rowTop, colW, photoH, center = true)
                else b.canvas.drawRect(RectF(x, rowTop, x + colW, rowTop + photoH), b.placeholder)
                b.drawLayout(texts[i], x, rowTop + photoH + 6f)
            }
            b.y = rowTop + rowH
        }
    }

    private class PdfBuilder {
        val doc = PdfDocument()
        val w = 595f
        val h = 842f
        val m = 36f
        var y = 0f
        private var page: PdfDocument.Page? = null
        private var pageNo = 0
        lateinit var canvas: Canvas

        val huge = paint(30f, true, Color.rgb(20, 80, 140))
        val title = paint(18f, true, Color.BLACK)
        val bold = paint(12f, true, Color.BLACK)
        val body = paint(10f, false, Color.rgb(34, 34, 34))
        val small = paint(8.5f, false, Color.rgb(60, 60, 60))
        val footer = paint(7.5f, false, Color.GRAY)
        val placeholder = Paint().apply { color = Color.rgb(235, 235, 235) }
        private val imgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        private fun paint(size: Float, isBold: Boolean, c: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = c
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun newPage() {
            finish()
            pageNo++
            val p = doc.startPage(PdfDocument.PageInfo.Builder(w.toInt(), h.toInt(), pageNo).create())
            page = p
            canvas = p.canvas
            y = m
            canvas.drawText("Boothly · page $pageNo", m, h - 16f, footer)
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }

        /** Starts a new page if [height] doesn't fit. Returns true if a page break happened. */
        fun ensure(height: Float): Boolean {
            if (y + height > h - m - 10f) {
                newPage()
                return true
            }
            return false
        }

        fun layout(text: CharSequence, p: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, maxOf(width, 10))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()

        fun drawLayout(l: StaticLayout, x: Float, top: Float) {
            canvas.save()
            canvas.translate(x, top)
            l.draw(canvas)
            canvas.restore()
        }

        fun text(t: String, p: TextPaint) {
            val l = layout(t, p, (w - 2 * m).toInt())
            ensure(l.height.toFloat())
            drawLayout(l, m, y)
            y += l.height
        }

        /** Draws an image fitted into the box. Returns the drawn height. */
        fun image(f: File, x: Float, top: Float, maxW: Float, maxH: Float, center: Boolean = false): Float {
            val bmp: Bitmap = Images.decodeSmall(f, (maxW * 1.5f).toInt(), (maxH * 1.5f).toInt()) ?: return 0f
            val scale = minOf(maxW / bmp.width, maxH / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val dx = if (center) x + (maxW - dw) / 2f else x
            val dy = if (center) top + (maxH - dh) / 2f else top
            canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), imgPaint)
            bmp.recycle()
            return if (center) maxH else dh
        }
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
