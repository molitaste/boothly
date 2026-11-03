package com.boothly.app.ocr

import kotlin.math.abs

data class ParsedCard(
    var company: String = "",
    var name: String = "",
    var title: String = "",
    val phones: MutableList<String> = mutableListOf(),
    val emails: MutableList<String> = mutableListOf(),
    var website: String = "",
    var wechat: String = "",
    var whatsapp: String = "",
    var address: String = "",
    var booth: String = "",
    val notes: MutableList<String> = mutableListOf(),
)

/** Turns OCR lines from a business card (Chinese / English / Arabic) into contact fields. */
object CardParser {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}")
    private val URL = Regex(
        "(?:https?://)?(?:www\\.)?[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\." +
            "(?:com|cn|net|org|hk|tw|co|io|biz|info|ae|sa|jo|eg|tr|in|de|uk|fr|it|es|ru|top|shop|asia|vip|cc|me|tech|store|online|site|xyz|ltd|group|work|club)" +
            "(?:\\.[a-z]{2})?(?![a-z0-9-])(?:/\\S*)?",
        IC
    )
    private val PHONE = Regex("(?<![A-Za-z0-9])(?:\\+|00)?\\(?\\d[\\d\\s\\-().]{5,}\\d")
    private val LABEL = Regex(
        "fax|传真|فاكس|tel|phone|mob|mobile|cell|电话|手机|座机|هاتف|جوال|موبايل|whats\\s*app|واتساب|واتس|we\\s?chat|微信|weixin|skype|hotline|热线",
        IC
    )
    private val FAX = Regex("fax|传真|فاكس", IC)
    private val WA_KEY = Regex("whats\\s*app|واتساب|واتس", IC)
    private val WC_KEY = Regex("we\\s?chat|微信|weixin|(?<![a-z])wx(?![a-z])", IC)
    private val WC_ID = Regex(
        "(?:we\\s?chat|微信|weixin|(?<![a-z])wx(?![a-z]))\\s*(?:id|号)?\\s*[:：]?\\s*([A-Za-z][A-Za-z0-9_-]{4,19})",
        IC
    )
    private val NOT_ID = Regex("whats\\s*app|tel|phone|mobile|email|e-mail|skype|line|viber|wechat|fax|number", IC)

    private val BOOTH = Regex(
        "(?:booth|stand|展位号?|摊位号?|جناح)\\s*(?:no\\.?|number|#)?\\s*[:：]?\\s*([A-Za-z0-9][A-Za-z0-9.\\-/]{1,14})",
        IC
    )
    private val CANTON_BOOTH = Regex("(?<![\\d.])\\d{1,2}\\.\\d\\s?[A-Z]\\s?\\d{2}(?:\\s?-\\s?\\d{2})?(?!\\d)")

    private val COMPANY_STRONG = Regex(
        "co\\.|co\\s*,|\\bltd\\b|limited|\\bcorp\\b|corporation|\\binc\\b|company|factory|有限公司|公司|厂|集团|商行|شركة|مؤسسة|مصنع",
        IC
    )
    private val COMPANY_WEAK = Regex(
        "industr|trading|technolog|manufactur|\\bgroup\\b|enterprise|electronic|lighting|imp\\.?\\s*&\\s*exp",
        IC
    )
    private val TITLE_KEYS = Regex(
        "manager|director|\\bceo\\b|\\bcoo\\b|\\bcfo\\b|president|founder|owner|\\bsales\\b|engineer|assistant|" +
            "representative|executive|officer|supervisor|\\bgm\\b|chairman|purchas|buyer|merchandiser|" +
            "经理|总监|总经理|董事长|主管|销售|业务|采购|工程师|助理|مدير|مبيعات|مندوب|رئيس",
        IC
    )
    private val ADDR_KEYS = Regex(
        "^\\s*(?:add|addr|address)\\b|address|\\broad\\b|\\brd\\b|street|\\bst\\.|avenue|\\bave\\b|district|province|" +
            "\\bcity\\b|\\btown\\b|village|industrial (?:park|zone|area)|building|\\bbldg\\b|floor|\\bfl\\b|\\broom\\b|" +
            "\\bno\\.\\s*\\d|地址|路|街|区|市|省|号|楼|镇|村|العنوان|شارع|مدينة|حي",
        IC
    )
    private val LABEL_PREFIX = Regex(
        "^\\s*(?:add(?:ress)?|addr|name|姓名|地址|العنوان|الاسم|title|职位|company|公司名称)\\s*[.:：]\\s*",
        IC
    )
    private val LATIN_NAME = Regex("^(?:(?:[Mm]rs?|[Mm]s|[Mm]iss|[Dd]r)\\.?\\s+)?[A-Z][a-zA-Z'\\-]+(?:\\s+[A-Z][a-zA-Z'\\-.]+){0,3}$")
    private val CHINESE_NAME = Regex("^[\\u4e00-\\u9fa5]{2,4}$")
    private val ARABIC_NAME = Regex("^[\\u0600-\\u06FF]+(?:\\s+[\\u0600-\\u06FF]+){1,3}$")
    private val NOT_NAME = Regex(
        "china|guangzhou|shenzhen|ningbo|foshan|zhongshan|dongguan|yiwu|xiamen|shanghai|beijing|hangzhou|" +
            "website|email|mobile|phone|products?|quality|welcome|thank|canton|fair|www",
        IC
    )

    fun parse(lines: List<String>, qr: List<String>): ParsedCard {
        val p = ParsedCard()
        qr.forEach { parseQr(it, p) }

        val clean = lines.map { it.trim() }.filter { it.isNotEmpty() }
        val used = BooleanArray(clean.size)

        // Pass 1: emails, websites, phones, WhatsApp, WeChat, booth
        clean.forEachIndexed { i, raw ->
            EMAIL.findAll(raw).forEach { addUnique(p.emails, it.value); used[i] = true }
            val line = EMAIL.replace(raw, " ")

            WC_ID.find(line)?.let { m ->
                val id = m.groupValues[1]
                if (!NOT_ID.matches(id) && p.wechat.isBlank()) {
                    p.wechat = id
                    used[i] = true
                }
            }

            URL.findAll(line).firstOrNull()?.let { m ->
                if (p.website.isBlank()) p.website = m.value.trimEnd('.', ',', ';')
                used[i] = true
            }

            CANTON_BOOTH.find(raw)?.let { m ->
                if (p.booth.isBlank()) p.booth = m.value.replace(" ", "")
                used[i] = true
            }
            BOOTH.find(raw)?.let { m ->
                if (p.booth.isBlank()) p.booth = m.groupValues[1]
                used[i] = true
            }

            val hasLabel = LABEL.containsMatchIn(line)
            val isAddress = ADDR_KEYS.containsMatchIn(line) && !hasLabel
            if (!isAddress) {
                PHONE.findAll(line).forEach { m ->
                    val value = m.value.trim()
                    val digits = value.count { it.isDigit() }
                    val intl = value.startsWith("+") || value.startsWith("00")
                    if (digits < 7 || digits > 15) return@forEach
                    if (!hasLabel && !intl && digits < 9) return@forEach
                    val before = line.substring(0, m.range.first)
                    val lastLabel = LABEL.findAll(before).lastOrNull()?.value
                    if (lastLabel != null && FAX.containsMatchIn(lastLabel)) return@forEach
                    val num = normalizePhone(value)
                    addUnique(p.phones, num)
                    used[i] = true
                    if (WA_KEY.containsMatchIn(line) && p.whatsapp.isBlank()) p.whatsapp = num
                    if (WC_KEY.containsMatchIn(line) && p.wechat.isBlank()) p.wechat = num.removePrefix("+86").removePrefix("+")
                }
            }
        }

        // Pass 2: company
        if (p.company.isBlank()) {
            val strong = clean.indices.filter { !used[it] && COMPANY_STRONG.containsMatchIn(clean[it]) }
            val idx = strong.firstOrNull { isMostlyLatin(clean[it]) }
                ?: strong.firstOrNull()
                ?: clean.indices.firstOrNull {
                    !used[it] && COMPANY_WEAK.containsMatchIn(clean[it]) &&
                        !TITLE_KEYS.containsMatchIn(clean[it]) && !ADDR_KEYS.containsMatchIn(clean[it])
                }
            if (idx != null) {
                p.company = stripLabel(clean[idx])
                used[idx] = true
            }
        }
        // the other-language version of the company name goes to notes
        clean.indices.filter { !used[it] && COMPANY_STRONG.containsMatchIn(clean[it]) }.forEach {
            p.notes.add(clean[it]); used[it] = true
        }

        // Pass 3: job title
        var titleIdx = -1
        if (p.title.isBlank()) {
            titleIdx = clean.indices.firstOrNull {
                !used[it] && TITLE_KEYS.containsMatchIn(clean[it]) && clean[it].length <= 45
            } ?: -1
            if (titleIdx >= 0) {
                p.title = stripLabel(clean[titleIdx])
                used[titleIdx] = true
            }
        }

        // Pass 4: person name (closest name-like line to the title)
        if (p.name.isBlank()) {
            fun pick(re: Regex): Int? {
                val c = clean.indices.filter { !used[it] && isNameLike(stripLabel(clean[it]), re) }
                return if (titleIdx >= 0) c.minByOrNull { abs(it - titleIdx) } else c.firstOrNull()
            }
            val latin = pick(LATIN_NAME)
            val chinese = pick(CHINESE_NAME)
            val arabic = pick(ARABIC_NAME)
            val parts = listOfNotNull(latin, chinese, if (latin == null && chinese == null) arabic else null)
            parts.forEach { used[it] = true }
            p.name = parts.joinToString(" ") { stripLabel(clean[it]) }
        }

        // Pass 5: address
        if (p.address.isBlank()) {
            val addr = clean.indices.filter { !used[it] && ADDR_KEYS.containsMatchIn(clean[it]) }.take(2)
            addr.forEach { used[it] = true }
            p.address = addr.joinToString(", ") { stripLabel(clean[it]) }
        }
        return p
    }

    private fun isNameLike(s: String, re: Regex): Boolean {
        if (s.length > 32 || !re.matches(s)) return false
        if (COMPANY_STRONG.containsMatchIn(s) || COMPANY_WEAK.containsMatchIn(s)) return false
        if (TITLE_KEYS.containsMatchIn(s) || ADDR_KEYS.containsMatchIn(s) || NOT_NAME.containsMatchIn(s)) return false
        return true
    }

    private fun isMostlyLatin(s: String): Boolean {
        val letters = s.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        return letters.count { it in 'A'..'Z' || it in 'a'..'z' } * 2 >= letters.length
    }

    private fun stripLabel(s: String) = s.replace(LABEL_PREFIX, "").trim()

    private fun addUnique(list: MutableList<String>, v: String) {
        val key = v.filter { it.isLetterOrDigit() }.lowercase()
        if (key.isEmpty()) return
        if (list.none { it.filter { c -> c.isLetterOrDigit() }.lowercase() == key }) list.add(v.trim())
    }

    fun normalizePhone(raw: String): String {
        val s = raw.replace("(0)", "").trim()
        val plus = s.startsWith("+")
        var d = s.filter { it.isDigit() }
        if (!plus && d.startsWith("00")) {
            d = d.drop(2)
            return "+$d"
        }
        return if (plus) "+$d" else d
    }

    private fun parseQr(value: String, p: ParsedCard) {
        val t = value.trim()
        when {
            t.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                t.lines().forEach { l ->
                    val idx = l.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = l.substring(0, idx).substringBefore(';').substringAfterLast('.').uppercase()
                    val v = l.substring(idx + 1).trim()
                    if (v.isEmpty()) return@forEach
                    when (key) {
                        "FN" -> if (p.name.isBlank()) p.name = v
                        "N" -> if (p.name.isBlank()) p.name = v.split(';').filter { it.isNotBlank() }.reversed().joinToString(" ")
                        "ORG" -> if (p.company.isBlank()) p.company = v.replace(';', ' ').trim()
                        "TITLE" -> if (p.title.isBlank()) p.title = v
                        "TEL" -> addUnique(p.phones, normalizePhone(v))
                        "EMAIL" -> addUnique(p.emails, v)
                        "URL" -> if (p.website.isBlank()) p.website = v
                        "ADR" -> if (p.address.isBlank()) p.address = v.split(';').filter { it.isNotBlank() }.joinToString(", ")
                    }
                }
            }
            t.startsWith("MECARD:", ignoreCase = true) -> {
                t.substring(7).split(';').forEach { f ->
                    val idx = f.indexOf(':')
                    if (idx <= 0) return@forEach
                    val key = f.substring(0, idx).uppercase()
                    val v = f.substring(idx + 1).trim()
                    if (v.isEmpty()) return@forEach
                    when (key) {
                        "N" -> if (p.name.isBlank()) p.name = v.split(',').reversed().joinToString(" ").trim()
                        "ORG" -> if (p.company.isBlank()) p.company = v
                        "TEL" -> addUnique(p.phones, normalizePhone(v))
                        "EMAIL" -> addUnique(p.emails, v)
                        "URL" -> if (p.website.isBlank()) p.website = v
                        "ADR" -> if (p.address.isBlank()) p.address = v
                    }
                }
            }
            t.contains("wa.me/", ignoreCase = true) || t.contains("whatsapp", ignoreCase = true) -> {
                val digits = Regex("(?:wa\\.me/|phone=)\\+?(\\d{7,15})").find(t)?.groupValues?.get(1)
                if (digits != null) {
                    if (p.whatsapp.isBlank()) p.whatsapp = "+$digits"
                    addUnique(p.phones, "+$digits")
                } else {
                    p.notes.add("WhatsApp QR: $t")
                }
            }
            t.contains("wechat", ignoreCase = true) || t.contains("weixin", ignoreCase = true) -> p.notes.add("WeChat QR: $t")
            t.startsWith("http", ignoreCase = true) || t.startsWith("www.", ignoreCase = true) -> {
                if (p.website.isBlank()) p.website = t else p.notes.add("QR: $t")
            }
            else -> p.notes.add("QR: $t")
        }
    }
}
