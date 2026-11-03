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
        val d = Repo.data.value
        val f = File(outDir(ctx), "Boothly_${stamp()}.xlsx")
        val byId = d.suppliers.associateBy { it.id }

        val productRows = mutableListOf<List<Any>>()
        productRows.add(listOf("Supplier", "Booth", "Contact", "Product", "Price", "Currency", "MOQ", "Product notes",
            "Phones", "Email", "WhatsApp", "WeChat", "Photos", "Added"))
        d.products.sortedBy { it.createdAt }.forEach { p ->
            val s = byId[p.supplierId]
            productRows.add(listOf(
                s?.company ?: "", s?.booth ?: "", s?.contactName ?: "", p.name,
                p.price.replace(",", "").trim().toDoubleOrNull() ?: p.price, p.currency, p.moq, p.notes,
                s?.phones?.joinToString(" / ") ?: "", s?.emails?.joinToString(" / ") ?: "",
                s?.whatsapp ?: "", s?.wechat ?: "", p.photos.size.toDouble(), date(p.createdAt)
            ))
        }

        val supplierRows = mutableListOf<List<Any>>()
        supplierRows.add(listOf("Company", "Contact", "Title", "Booth", "Phones", "Emails", "WhatsApp", "WeChat",
            "Website", "Address", "Notes", "Products", "Added"))
        d.suppliers.sortedBy { it.createdAt }.forEach { s ->
            supplierRows.add(listOf(
                s.company, s.contactName, s.title, s.booth, s.phones.joinToString(" / "), s.emails.joinToString(" / "),
                s.whatsapp, s.wechat, s.website, s.address, s.notes,
                d.products.count { it.supplierId == s.id }.toDouble(), date(s.createdAt)
            ))
        }

        ZipOutputStream(FileOutputStream(f)).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(content.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", CONTENT_TYPES)
            put("_rels/.rels", ROOT_RELS)
            put("xl/workbook.xml", WORKBOOK)
            put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
            put("xl/styles.xml", STYLES)
            put("xl/worksheets/sheet1.xml", sheetXml(productRows))
            put("xl/worksheets/sheet2.xml", sheetXml(supplierRows))
        }
        return f
    }

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun xml(s: String): String {
        val sb = StringBuilder(s.length + 16)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(ch)
                ch.code < 0x20 || ch.code == 0xFFFE || ch.code == 0xFFFF -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun sheetXml(rows: List<List<Any>>): String {
        val cols = rows.firstOrNull()?.size ?: 1
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("""<cols><col min="1" max="$cols" width="24" customWidth="1"/></cols><sheetData>""")
        rows.forEachIndexed { r, row ->
            val rn = r + 1
            sb.append("""<row r="$rn">""")
            row.forEachIndexed { c, v ->
                val ref = col(c) + rn
                val style = if (r == 0) " s=\"1\"" else ""
                if (v is Double) {
                    val num = if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
                    sb.append("""<c r="$ref"$style><v>$num</v></c>""")
                } else {
                    val text = v.toString()
                    if (text.isNotEmpty()) {
                        sb.append("""<c r="$ref" t="inlineStr"$style><is><t xml:space="preserve">${xml(text)}</t></is></c>""")
                    }
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private const val WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Products" sheetId="1" r:id="rId1"/><sheet name="Suppliers" sheetId="2" r:id="rId2"/></sheets></workbook>"""
    private const val WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs></styleSheet>"""

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
