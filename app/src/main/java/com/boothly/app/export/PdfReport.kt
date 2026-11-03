package com.boothly.app.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.boothly.app.data.AppData
import com.boothly.app.data.Product
import com.boothly.app.data.Repo
import com.boothly.app.data.Supplier
import com.boothly.app.util.CityDetector
import com.boothly.app.util.Images
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Professional A4 PDF report in the Boothly aqua / coral style. */
internal object PdfReport {
    private const val W = 595f
    private const val H = 842f
    private const val M = 36f
    private const val CONTENT_W = W - 2 * M
    private const val FOOTER_Y = H - 30f

    private val AQUA = Color.rgb(0x14, 0xB8, 0xC4)
    private val AQUA_DARK = Color.rgb(0x0E, 0x8F, 0x99)
    private val AQUA_LIGHT = Color.rgb(0xE3, 0xF6, 0xF7)
    private val AQUA_BORDER = Color.rgb(0xCD, 0xEF, 0xF1)
    private val CORAL = Color.rgb(0xE8, 0x5D, 0x3F)
    private val CORAL_LIGHT = Color.rgb(0xFF, 0xF1, 0xEC)
    private val INK = Color.rgb(0x1B, 0x2B, 0x3C)
    private val MUTED = Color.rgb(0x6B, 0x77, 0x85)
    private val ROW_ALT = Color.rgb(0xF6, 0xFB, 0xFB)

    private class Ctx {
        val doc = PdfDocument()
        var page: PdfDocument.Page? = null
        lateinit var c: Canvas
        var no = 0
        var y = 0f
    }

    private fun tp(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
    private fun stroke(color: Int, w: Float = 0.8f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = w }

    private fun layout(text: CharSequence, p: TextPaint, width: Float, maxLines: Int = Int.MAX_VALUE, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(10))
            .setAlignment(align)
            .setLineSpacing(1f, 1.12f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

    private fun draw(ctx: Ctx, l: StaticLayout, x: Float, y: Float) {
        ctx.c.save()
        ctx.c.translate(x, y)
        l.draw(ctx.c)
        ctx.c.restore()
    }

    private fun newPage(ctx: Ctx) {
        ctx.page?.let { ctx.doc.finishPage(it) }
        ctx.no++
        val p = ctx.doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), ctx.no).create())
        ctx.page = p
        ctx.c = p.canvas
        ctx.c.drawColor(Color.WHITE)
        // footer
        ctx.c.drawLine(M, FOOTER_Y - 8, W - M, FOOTER_Y - 8, stroke(AQUA_BORDER))
        ctx.c.drawText("Boothly · Trade fair report", M, FOOTER_Y + 4, tp(7.5f, MUTED))
        val pg = tp(7.5f, MUTED)
        val t = "Page ${ctx.no}"
        ctx.c.drawText(t, W - M - pg.measureText(t), FOOTER_Y + 4, pg)
        ctx.y = M
    }

    private fun bottomLimit() = FOOTER_Y - 18

    // ---------------------------------------------------------------- images

    /** Draw picture covering the box (center-cropped) with rounded corners. */
    private fun imageCover(ctx: Ctx, f: File, box: RectF, radius: Float = 8f) {
        val bmp = Images.decodeSmall(f, (box.width() * 2).toInt(), (box.height() * 2).toInt())
        ctx.c.save()
        val clip = Path().apply { addRoundRect(box, radius, radius, Path.Direction.CW) }
        ctx.c.clipPath(clip)
        if (bmp == null) {
            ctx.c.drawRect(box, fill(AQUA_LIGHT))
        } else {
            val boxR = box.width() / box.height()
            val bmpR = bmp.width.toFloat() / bmp.height
            val src = if (bmpR > boxR) {
                val w = (bmp.height * boxR).toInt()
                val x = (bmp.width - w) / 2
                Rect(x, 0, x + w, bmp.height)
            } else {
                val h = (bmp.width / boxR).toInt()
                val y = (bmp.height - h) / 2
                Rect(0, y, bmp.width, y + h)
            }
            ctx.c.drawBitmap(bmp, src, box, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bmp.recycle()
        }
        ctx.c.restore()
    }

    /** Draw picture fitted inside the box (whole picture visible) on a light background. Returns used height. */
    private fun imageFit(ctx: Ctx, f: File, box: RectF, radius: Float = 8f): Float {
        val bmp: Bitmap = Images.decodeSmall(f, (box.width() * 2).toInt(), (box.height() * 2).toInt()) ?: return 0f
        val scale = minOf(box.width() / bmp.width, box.height() / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val dst = RectF(box.left, box.top, box.left + w, box.top + h)
        dst.offset((box.width() - w) / 2f, 0f)
        ctx.c.save()
        ctx.c.clipPath(Path().apply { addRoundRect(dst, radius, radius, Path.Direction.CW) })
        ctx.c.drawBitmap(bmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        ctx.c.restore()
        ctx.c.drawRoundRect(dst, radius, radius, stroke(AQUA_BORDER, 1f))
        bmp.recycle()
        return h
    }

    // ---------------------------------------------------------------- report (compact, many suppliers per page)

    fun build(out: File) {
        val d: AppData = Repo.data.value
        val ctx = Ctx()
        val suppliers = d.suppliers.sortedWith(compareBy<Supplier>({ CityDetector.cityOf(it).ifBlank { "~" } }, { it.createdAt }))

        newPage(ctx)
        reportHeader(ctx, d, suppliers)
        suppliers.forEachIndexed { i, s ->
            supplierBlock(ctx, i + 1, s, d.products.filter { it.supplierId == s.id }.sortedBy { it.createdAt })
        }
        if (suppliers.isEmpty()) ctx.c.drawText("No suppliers recorded yet.", M, ctx.y + 10f, tp(10f, MUTED))

        ctx.page?.let { ctx.doc.finishPage(it) }
        FileOutputStream(out).use { ctx.doc.writeTo(it) }
        ctx.doc.close()
    }

    /** Short header on the first page: title, date and totals. */
    private fun reportHeader(ctx: Ctx, d: AppData, suppliers: List<Supplier>) {
        val c = ctx.c
        val bandH = 92f
        c.drawRect(0f, 0f, W, bandH, fill(AQUA))
        c.drawCircle(W - 50f, 0f, 70f, fill(Color.argb(28, 255, 255, 255)))
        c.drawText("BOOTHLY", M, 28f, tp(8.5f, Color.WHITE, true).apply { letterSpacing = 0.25f })
        c.drawText("Trade fair report", M, 56f, tp(22f, Color.WHITE, true))
        c.drawText(SimpleDateFormat("d MMMM yyyy", Locale.US).format(Date()), M, 75f, tp(10f, Color.WHITE))

        // totals as white chips on the right
        val cities = suppliers.map { CityDetector.cityOf(it) }.filter { it.isNotBlank() }.toSet().size
        val chips = listOf("${d.suppliers.size}" to "suppliers", "${d.products.size}" to "products", "$cities" to "cities")
        var x = W - M
        chips.reversed().forEach { (n, label) ->
            val np = tp(15f, AQUA_DARK, true)
            val lp = tp(7.5f, MUTED)
            val w = maxOf(np.measureText(n), lp.measureText(label)) + 22f
            val r = RectF(x - w, 30f, x, 72f)
            c.drawRoundRect(r, 10f, 10f, fill(Color.WHITE))
            c.drawText(n, r.left + 11f, r.top + 21f, np)
            c.drawText(label, r.left + 11f, r.top + 34f, lp)
            x -= w + 8f
        }
        ctx.y = bandH + 16f
    }

    private val CARD_W = 118f
    private val CARD_H = 74f
    private const val COLS = 4
    private const val CELL_GAP = 8f
    private const val PHOTO_H = 88f

    private fun supplierBar(ctx: Ctx, idx: Int, s: Supplier, productCount: Int, continued: Boolean) {
        val r = RectF(M, ctx.y, W - M, ctx.y + 24f)
        ctx.c.drawRoundRect(r, 7f, 7f, fill(AQUA))
        val num = "$idx"
        val np = tp(8.5f, AQUA_DARK, true)
        ctx.c.drawCircle(r.left + 13f, r.centerY(), 8f, fill(Color.WHITE))
        ctx.c.drawText(num, r.left + 13f - np.measureText(num) / 2, r.centerY() + 3f, np)
        val city = CityDetector.cityOf(s)
        val right = if (continued) "continued" else listOfNotNull(
            s.booth.takeIf { it.isNotBlank() }?.let { "Booth $it" },
            city.ifBlank { null },
            "$productCount product" + if (productCount == 1) "" else "s",
        ).joinToString("  ·  ")
        val rp = tp(8.5f, Color.WHITE)
        val rw = rp.measureText(right)
        ctx.c.drawText(right, r.right - 10f - rw, r.centerY() + 3f, rp)
        draw(ctx, layout(s.displayName, tp(11f, Color.WHITE, true), r.width() - rw - 50f, 1), r.left + 28f, r.top + 5f)
        ctx.y = r.bottom + 8f
    }

    private fun supplierBlock(ctx: Ctx, idx: Int, s: Supplier, products: List<Product>) {
        // ---- details: label/value pairs flowing in two columns, card picture on the right
        val pairs = listOf(
            "Contact" to listOf(s.contactName, s.title).filter { it.isNotBlank() }.joinToString(" · "),
            "Company" to (if (s.company.isNotBlank() && s.company != s.displayName) s.company else ""),
            "Phone" to s.phones.joinToString(", "),
            "WhatsApp" to s.whatsapp,
            "WeChat" to s.wechat,
            "Email" to s.emails.joinToString(", "),
            "Website" to s.website,
            "Address" to s.address,
        ).filter { it.second.isNotBlank() }
        val hasCard = s.cardImages.isNotEmpty()
        val detailsW = CONTENT_W - if (hasCard) CARD_W + 12f else 0f
        val colW = (detailsW - 10f) / 2f
        val labelP = tp(6.8f, MUTED, true)
        val valueP = tp(8.6f, INK)
        // address & email can be long: give them a full row
        val cells = pairs.map { (l, v) ->
            val full = l == "Address" || v.length > 34
            Triple(l, layout(v, valueP, (if (full) detailsW else colW) - 2f, 2), full)
        }
        // compute rows
        data class Placed(val label: String, val l: StaticLayout, val x: Float, val y: Float)
        val placed = mutableListOf<Placed>()
        var cy = 0f
        var col = 0
        var rowH = 0f
        cells.forEach { (label, l, full) ->
            if (full && col == 1) { cy += rowH; col = 0; rowH = 0f }
            val h = 9f + l.height + 5f
            placed += Placed(label, l, if (col == 0) 0f else colW + 10f, cy)
            rowH = maxOf(rowH, h)
            if (full || col == 1) { cy += rowH; col = 0; rowH = 0f } else col = 1
        }
        cy += rowH
        val notesL = if (s.notes.isNotBlank()) layout(s.notes, tp(8.4f, INK), detailsW - 20f, 3) else null
        val notesH = if (notesL != null) notesL.height + 12f else 0f
        val detailsH = maxOf(cy + (if (notesL != null) notesH + 4f else 0f), if (hasCard) CARD_H else 0f)

        // keep the supplier bar + details (+ first product row) together
        val firstRow = if (products.isNotEmpty()) productRowHeight(products.take(COLS)) + 6f else 0f
        if (ctx.y + 32f + detailsH + firstRow > bottomLimit()) newPage(ctx)

        supplierBar(ctx, idx, s, products.size, continued = false)
        val top = ctx.y
        placed.forEach { p ->
            ctx.c.drawText(p.label.uppercase(), M + p.x, top + p.y + 7f, labelP)
            draw(ctx, p.l, M + p.x, top + p.y + 9f)
        }
        if (notesL != null) {
            val r = RectF(M, top + cy + 2f, M + detailsW, top + cy + 2f + notesH)
            ctx.c.drawRoundRect(r, 6f, 6f, fill(CORAL_LIGHT))
            ctx.c.drawRect(r.left, r.top + 4f, r.left + 2.5f, r.bottom - 4f, fill(CORAL))
            draw(ctx, notesL, r.left + 10f, r.top + 6f)
        }
        if (hasCard) {
            val box = RectF(W - M - CARD_W, top, W - M, top + CARD_H)
            imageFit(ctx, Repo.photoFile(s.cardImages.first()), box, 6f)
        }
        ctx.y = top + detailsH + 8f

        // ---- products: compact cards, 4 per row
        products.chunked(COLS).forEach { row ->
            val h = productRowHeight(row)
            if (ctx.y + h > bottomLimit()) {
                newPage(ctx)
                supplierBar(ctx, idx, s, products.size, continued = true)
            }
            val cellW = (CONTENT_W - (COLS - 1) * CELL_GAP) / COLS
            row.forEachIndexed { i, p ->
                val x = M + i * (cellW + CELL_GAP)
                val r = RectF(x, ctx.y, x + cellW, ctx.y + h)
                ctx.c.drawRoundRect(r, 8f, 8f, fill(Color.WHITE))
                ctx.c.drawRoundRect(r, 8f, 8f, stroke(AQUA_BORDER, 0.8f))
                val pr = RectF(x + 4f, ctx.y + 4f, x + cellW - 4f, ctx.y + 4f + PHOTO_H)
                val photo = p.photos.firstOrNull()
                if (photo != null) imageCover(ctx, Repo.photoFile(photo), pr, 6f)
                else {
                    ctx.c.drawRoundRect(pr, 6f, 6f, fill(AQUA_LIGHT))
                    val np = tp(7.5f, AQUA_DARK)
                    ctx.c.drawText("No photo", pr.centerX() - np.measureText("No photo") / 2, pr.centerY() + 3f, np)
                }
                var ty = pr.bottom + 5f
                productTexts(p, cellW - 12f).forEach { l ->
                    draw(ctx, l, x + 6f, ty)
                    ty += l.height + 1.5f
                }
            }
            ctx.y += h + 6f
        }

        // divider before the next supplier
        ctx.y += 4f
        if (ctx.y + 10f < bottomLimit()) ctx.c.drawLine(M, ctx.y, W - M, ctx.y, stroke(AQUA_BORDER, 0.6f))
        ctx.y += 12f
    }

    private fun productRowHeight(row: List<Product>): Float {
        val cellW = (CONTENT_W - (COLS - 1) * CELL_GAP) / COLS
        val textH = row.maxOf { p -> productTexts(p, cellW - 12f).sumOf { it.height.toDouble() + 1.5 }.toFloat() }
        return 4f + PHOTO_H + 5f + textH + 6f
    }

    private fun productTexts(p: Product, width: Float): List<StaticLayout> {
        val list = mutableListOf<StaticLayout>()
        list += layout(p.displayName, tp(8.6f, INK, true), width, 2)
        val priceLine = buildString {
            append(if (p.priceLabel.isNotBlank()) p.priceLabel else "–")
        }
        list += layout(priceLine, tp(10.5f, if (p.priceLabel.isNotBlank()) CORAL else MUTED, true), width, 1)
        if (p.moq.isNotBlank()) list += layout("MOQ ${p.moq}", tp(7.5f, AQUA_DARK, true), width, 1)
        if (p.notes.isNotBlank()) list += layout(p.notes, tp(7.2f, MUTED), width, 2)
        return list
    }
}
