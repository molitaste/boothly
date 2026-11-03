package com.boothly.app.export

import android.graphics.Bitmap
import com.boothly.app.data.AppData
import com.boothly.app.data.Product
import com.boothly.app.data.Repo
import com.boothly.app.data.Supplier
import com.boothly.app.util.CityDetector
import com.boothly.app.util.Images
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds a nicely formatted .xlsx with embedded pictures (no external library):
 *  Sheet 1 "Fair report": one vertical block per supplier – details + business card, then its products with photos.
 *  Sheet 2 "All products": one row per product with photo, for comparing prices.
 */
internal object XlsxReport {

    // Style ids (see STYLES below)
    private const val S_TITLE = 1      // white bold on turquoise
    private const val S_LABEL = 2      // bold, light fill, border
    private const val S_VALUE = 3      // wrap, border
    private const val S_HEAD = 4       // table header
    private const val S_PRICE = 5      // bold coral number
    private const val S_CENTER = 6     // centred wrap
    private const val S_TEXT = 7       // wrap, vertical centre
    private const val S_SMALL = 8      // small grey
    private const val S_BIG = 9        // big bold title

    private class Pic(val media: Int, val col: Int, val row: Int, val offX: Int, val offY: Int, val w: Int, val h: Int)

    private class Sheet(val name: String, val widths: List<Double>) {
        val rows = StringBuilder()
        val merges = mutableListOf<String>()
        val pics = mutableListOf<Pic>()
        var r = 0 // next row index (0-based)

        fun row(cells: List<Pair<Any?, Int>>, heightPt: Double? = null) {
            val rn = r + 1
            rows.append("<row r=\"$rn\"")
            if (heightPt != null) rows.append(" ht=\"$heightPt\" customHeight=\"1\"")
            rows.append(">")
            cells.forEachIndexed { c, (v, style) ->
                val ref = col(c) + rn
                when {
                    v == null || (v is String && v.isEmpty()) -> rows.append("<c r=\"$ref\" s=\"$style\"/>")
                    v is Double -> rows.append("<c r=\"$ref\" s=\"$style\"><v>${num(v)}</v></c>")
                    else -> rows.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v.toString())}</t></is></c>")
                }
            }
            rows.append("</row>")
            r++
        }

        fun blank(heightPt: Double = 10.0) = row(emptyList(), heightPt)

        fun merge(c1: Int, r1: Int, c2: Int, r2: Int) {
            merges += "${col(c1)}${r1 + 1}:${col(c2)}${r2 + 1}"
        }

        /** Column left edge in pixels (Excel: px ≈ width*7+5). */
        fun colPx(c: Int) = (widths.getOrElse(c) { 10.0 } * 7 + 5).toInt()
    }

    private val media = mutableListOf<ByteArray>()

    /** Adds a picture scaled to fit (maxW × maxH) px. Returns the drawn (w, h) or null. */
    private fun addPic(sheet: Sheet, file: File, col: Int, row: Int, maxW: Int, maxH: Int, offX: Int = 4, offY: Int = 4): Pair<Int, Int>? {
        val bmp = Images.decodeSmall(file, maxW * 2, maxH * 2) ?: return null
        return try {
            val scale = minOf(maxW.toFloat() / bmp.width, maxH.toFloat() / bmp.height)
            val w = (bmp.width * scale).toInt().coerceAtLeast(1)
            val h = (bmp.height * scale).toInt().coerceAtLeast(1)
            // store at 2x for sharpness
            val stored = Bitmap.createScaledBitmap(bmp, (w * 2).coerceAtMost(bmp.width), (h * 2).coerceAtMost(bmp.height), true)
            val out = ByteArrayOutputStream()
            stored.compress(Bitmap.CompressFormat.JPEG, 80, out)
            if (stored !== bmp) stored.recycle()
            media += out.toByteArray()
            sheet.pics += Pic(media.size, col, row, offX, offY, w, h)
            w to h
        } finally {
            bmp.recycle()
        }
    }

    private fun date(t: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(t))

    private fun priceValue(p: Product): Any? = p.price.replace(",", "").trim().toDoubleOrNull() ?: p.price

    fun build(out: File) {
        media.clear()
        val d: AppData = Repo.data.value
        val suppliers = d.suppliers.sortedWith(compareBy<Supplier>({ CityDetector.cityOf(it).ifBlank { "~" } }, { it.createdAt }))

        // ---------------- Sheet 1: vertical report ----------------
        // A: label / photo, B: value / product, C: price, D: currency, E: MOQ, F: notes
        val rep = Sheet("Fair report", listOf(18.0, 28.0, 11.0, 9.0, 11.0, 26.0))
        rep.row(listOf("Boothly – trade fair report" to S_BIG), 30.0)
        rep.merge(0, 0, 5, 0)
        rep.row(listOf("${d.suppliers.size} suppliers · ${d.products.size} products · ${date(System.currentTimeMillis())}" to S_SMALL), 18.0)
        rep.merge(0, 1, 5, 1)
        rep.blank(12.0)

        suppliers.forEachIndexed { idx, s ->
            val products = d.products.filter { it.supplierId == s.id }.sortedBy { it.createdAt }

            // Title bar
            val city = CityDetector.cityOf(s)
            val title = listOf("${idx + 1}. ${s.displayName}", s.booth.takeIf { it.isNotBlank() }?.let { "Booth $it" }, city.ifBlank { null })
                .filterNotNull().joinToString("   ·   ")
            rep.row(listOf(title to S_TITLE, "" to S_TITLE, "" to S_TITLE, "" to S_TITLE, "" to S_TITLE, "" to S_TITLE), 26.0)
            rep.merge(0, rep.r - 1, 5, rep.r - 1)

            // Details (label | value), business card on the right (columns C..F)
            val details = listOf(
                "Company" to s.company,
                "Contact" to s.contactName,
                "Job title" to s.title,
                "Booth" to s.booth,
                "City" to city,
                "Phone" to s.phones.joinToString("\n"),
                "Email" to s.emails.joinToString("\n"),
                "WhatsApp" to s.whatsapp,
                "WeChat" to s.wechat,
                "Website" to s.website,
                "Address" to s.address,
                "Notes" to s.notes,
            ).filter { it.second.isNotBlank() }
            val firstDetailRow = rep.r
            details.forEach { (label, value) ->
                val lines = value.count { it == '\n' } + 1 + value.length / 38
                rep.row(listOf(label to S_LABEL, value to S_VALUE), (16.0 * lines.coerceIn(1, 6)).coerceAtLeast(18.0))
            }
            // make sure the card picture has room
            val minRows = 8
            repeat((minRows - details.size).coerceAtLeast(0)) { rep.row(emptyList(), 18.0) }
            s.cardImages.firstOrNull()?.let { card ->
                val maxW = rep.colPx(2) + rep.colPx(3) + rep.colPx(4) + rep.colPx(5) - 20
                addPic(rep, Repo.photoFile(card), 2, firstDetailRow, minOf(maxW, 330), 190, offX = 12, offY = 2)
            }
            rep.blank(8.0)

            // Products table
            if (products.isEmpty()) {
                rep.row(listOf("No products recorded" to S_SMALL), 18.0)
            } else {
                rep.row(
                    listOf("Photo" to S_HEAD, "Product" to S_HEAD, "Price" to S_HEAD, "Currency" to S_HEAD, "MOQ" to S_HEAD, "Notes" to S_HEAD),
                    20.0,
                )
                products.forEach { p ->
                    val rowIdx = rep.r
                    rep.row(
                        listOf(
                            "" to S_CENTER,
                            p.displayName to S_TEXT,
                            priceValue(p) to S_PRICE,
                            p.currency to S_CENTER,
                            p.moq to S_CENTER,
                            p.notes to S_TEXT,
                        ),
                        80.0,
                    )
                    p.photos.firstOrNull()?.let { ph -> addPic(rep, Repo.photoFile(ph), 0, rowIdx, rep.colPx(0) - 10, 100, offX = 5, offY = 3) }
                }
            }
            rep.blank(22.0)
        }

        // ---------------- Sheet 2: all products ----------------
        val all = Sheet("All products", listOf(20.0, 26.0, 12.0, 26.0, 11.0, 9.0, 11.0, 30.0, 20.0, 16.0, 18.0))
        all.row(
            listOf("Photo", "Supplier", "Booth", "Product", "Price", "Currency", "MOQ", "Notes", "Phone", "WeChat", "WhatsApp").map { it to S_HEAD },
            22.0,
        )
        val byId = d.suppliers.associateBy { it.id }
        d.products.sortedBy { it.createdAt }.forEach { p ->
            val s = byId[p.supplierId]
            val rowIdx = all.r
            all.row(
                listOf(
                    "" to S_CENTER,
                    (s?.displayName ?: "") to S_TEXT,
                    (s?.booth ?: "") to S_CENTER,
                    p.displayName to S_TEXT,
                    priceValue(p) to S_PRICE,
                    p.currency to S_CENTER,
                    p.moq to S_CENTER,
                    p.notes to S_TEXT,
                    (s?.phones?.firstOrNull() ?: "") to S_TEXT,
                    (s?.wechat ?: "") to S_TEXT,
                    (s?.whatsapp ?: "") to S_TEXT,
                ),
                80.0,
            )
            p.photos.firstOrNull()?.let { ph -> addPic(all, Repo.photoFile(ph), 0, rowIdx, all.colPx(0) - 10, 100, offX = 5, offY = 3) }
        }

        write(out, listOf(rep, all))
    }

    // ---------------------------------------------------------------- writing the package

    private fun write(out: File, sheets: List<Sheet>) {
        ZipOutputStream(FileOutputStream(out)).use { z ->
            fun put(name: String, content: String) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            fun putBytes(name: String, bytes: ByteArray) {
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }

            val ct = StringBuilder()
            ct.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            ct.append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpeg" ContentType="image/jpeg"/>""")
            ct.append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            ct.append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
            sheets.forEachIndexed { i, sh ->
                ct.append("""<Override PartName="/xl/worksheets/sheet${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                if (sh.pics.isNotEmpty()) ct.append("""<Override PartName="/xl/drawings/drawing${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())

            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")

            val wb = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
            val wbRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.forEachIndexed { i, sh ->
                wb.append("""<sheet name="${xml(sh.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
                wbRels.append("""<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""")
            }
            wb.append("</sheets></workbook>")
            wbRels.append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/workbook.xml", wb.toString())
            put("xl/_rels/workbook.xml.rels", wbRels.toString())
            put("xl/styles.xml", STYLES)

            media.forEachIndexed { i, bytes -> putBytes("xl/media/image${i + 1}.jpeg", bytes) }

            sheets.forEachIndexed { i, sh ->
                val n = i + 1
                val ws = StringBuilder()
                ws.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                ws.append("""<sheetPr><pageSetUpPr fitToPage="1"/></sheetPr>""")
                ws.append("""<sheetViews><sheetView workbookViewId="0" showGridLines="0"/></sheetViews>""")
                ws.append("<cols>")
                sh.widths.forEachIndexed { c, w -> ws.append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""") }
                ws.append("</cols><sheetData>").append(sh.rows).append("</sheetData>")
                if (sh.merges.isNotEmpty()) {
                    ws.append("""<mergeCells count="${sh.merges.size}">""")
                    sh.merges.forEach { ws.append("""<mergeCell ref="$it"/>""") }
                    ws.append("</mergeCells>")
                }
                ws.append("""<pageMargins left="0.4" right="0.4" top="0.5" bottom="0.5" header="0.3" footer="0.3"/>""")
                ws.append("""<pageSetup orientation="portrait" fitToWidth="1" fitToHeight="0" paperSize="9"/>""")
                if (sh.pics.isNotEmpty()) ws.append("""<drawing r:id="rId1"/>""")
                ws.append("</worksheet>")
                put("xl/worksheets/sheet$n.xml", ws.toString())

                if (sh.pics.isNotEmpty()) {
                    put("xl/worksheets/_rels/sheet$n.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$n.xml"/></Relationships>""")
                    val dr = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                    val drRels = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                    sh.pics.forEachIndexed { k, p ->
                        val rid = "rId${k + 1}"
                        val emu = 9525L
                        val cx = p.w * emu
                        val cy = p.h * emu
                        dr.append("<xdr:oneCellAnchor><xdr:from><xdr:col>${p.col}</xdr:col><xdr:colOff>${p.offX * emu}</xdr:colOff><xdr:row>${p.row}</xdr:row><xdr:rowOff>${p.offY * emu}</xdr:rowOff></xdr:from>")
                        dr.append("<xdr:ext cx=\"$cx\" cy=\"$cy\"/>")
                        dr.append("<xdr:pic><xdr:nvPicPr><xdr:cNvPr id=\"${k + 2}\" name=\"Picture ${k + 1}\"/><xdr:cNvPicPr><a:picLocks noChangeAspect=\"1\"/></xdr:cNvPicPr></xdr:nvPicPr>")
                        dr.append("<xdr:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>")
                        dr.append("<xdr:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></xdr:spPr></xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
                        drRels.append("""<Relationship Id="$rid" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image${p.media}.jpeg"/>""")
                    }
                    dr.append("</xdr:wsDr>")
                    drRels.append("</Relationships>")
                    put("xl/drawings/drawing$n.xml", dr.toString())
                    put("xl/drawings/_rels/drawing$n.xml.rels", drRels.toString())
                }
            }
        }
        media.clear()
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

    private fun num(v: Double): String =
        if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

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

    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts><fonts count="6"><font><sz val="11"/><color rgb="FF1B2B3C"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF0B7780"/><name val="Calibri"/></font><font><b/><sz val="13"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font><font><b/><sz val="12"/><color rgb="FFE0552F"/><name val="Calibri"/></font><font><sz val="10"/><color rgb="FF6B7785"/><name val="Calibri"/></font><font><b/><sz val="18"/><color rgb="FF0E8F99"/><name val="Calibri"/></font></fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF14B8C4"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE3F6F7"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFFFF1EC"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color rgb="FFCDEFF1"/></left><right style="thin"><color rgb="FFCDEFF1"/></right><top style="thin"><color rgb="FFCDEFF1"/></top><bottom style="thin"><color rgb="FFCDEFF1"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="10"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center" indent="1"/></xf><xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="top" indent="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="top" wrapText="1"/></xf><xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="164" fontId="3" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center" wrapText="1"/></xf><xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="0" fontId="5" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="center"/></xf></cellXfs></styleSheet>"""
}
