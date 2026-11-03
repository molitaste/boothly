package com.boothly.app.export

/**
 * Converts Arabic text into its connected letter shapes (Unicode "presentation forms"),
 * so it shows joined even in viewers that don't join Arabic letters themselves
 * (some phone spreadsheet viewers). Text order stays logical; the cell is marked right-to-left.
 */
internal object ArabicShaper {
    // letter -> [isolated, final, initial, medial]; 2 entries = letter never joins to the next one
    private val FORMS: Map<Char, IntArray> = mapOf(
        'ء' to intArrayOf(0xFE80),
        'آ' to intArrayOf(0xFE81, 0xFE82),
        'أ' to intArrayOf(0xFE83, 0xFE84),
        'ؤ' to intArrayOf(0xFE85, 0xFE86),
        'إ' to intArrayOf(0xFE87, 0xFE88),
        'ئ' to intArrayOf(0xFE89, 0xFE8A, 0xFE8B, 0xFE8C),
        'ا' to intArrayOf(0xFE8D, 0xFE8E),
        'ب' to intArrayOf(0xFE8F, 0xFE90, 0xFE91, 0xFE92),
        'ة' to intArrayOf(0xFE93, 0xFE94),
        'ت' to intArrayOf(0xFE95, 0xFE96, 0xFE97, 0xFE98),
        'ث' to intArrayOf(0xFE99, 0xFE9A, 0xFE9B, 0xFE9C),
        'ج' to intArrayOf(0xFE9D, 0xFE9E, 0xFE9F, 0xFEA0),
        'ح' to intArrayOf(0xFEA1, 0xFEA2, 0xFEA3, 0xFEA4),
        'خ' to intArrayOf(0xFEA5, 0xFEA6, 0xFEA7, 0xFEA8),
        'د' to intArrayOf(0xFEA9, 0xFEAA),
        'ذ' to intArrayOf(0xFEAB, 0xFEAC),
        'ر' to intArrayOf(0xFEAD, 0xFEAE),
        'ز' to intArrayOf(0xFEAF, 0xFEB0),
        'س' to intArrayOf(0xFEB1, 0xFEB2, 0xFEB3, 0xFEB4),
        'ش' to intArrayOf(0xFEB5, 0xFEB6, 0xFEB7, 0xFEB8),
        'ص' to intArrayOf(0xFEB9, 0xFEBA, 0xFEBB, 0xFEBC),
        'ض' to intArrayOf(0xFEBD, 0xFEBE, 0xFEBF, 0xFEC0),
        'ط' to intArrayOf(0xFEC1, 0xFEC2, 0xFEC3, 0xFEC4),
        'ظ' to intArrayOf(0xFEC5, 0xFEC6, 0xFEC7, 0xFEC8),
        'ع' to intArrayOf(0xFEC9, 0xFECA, 0xFECB, 0xFECC),
        'غ' to intArrayOf(0xFECD, 0xFECE, 0xFECF, 0xFED0),
        'ف' to intArrayOf(0xFED1, 0xFED2, 0xFED3, 0xFED4),
        'ق' to intArrayOf(0xFED5, 0xFED6, 0xFED7, 0xFED8),
        'ك' to intArrayOf(0xFED9, 0xFEDA, 0xFEDB, 0xFEDC),
        'ل' to intArrayOf(0xFEDD, 0xFEDE, 0xFEDF, 0xFEE0),
        'م' to intArrayOf(0xFEE1, 0xFEE2, 0xFEE3, 0xFEE4),
        'ن' to intArrayOf(0xFEE5, 0xFEE6, 0xFEE7, 0xFEE8),
        'ه' to intArrayOf(0xFEE9, 0xFEEA, 0xFEEB, 0xFEEC),
        'و' to intArrayOf(0xFEED, 0xFEEE),
        'ى' to intArrayOf(0xFEEF, 0xFEF0),
        'ي' to intArrayOf(0xFEF1, 0xFEF2, 0xFEF3, 0xFEF4),
        // Persian / Urdu letters sometimes seen on cards
        'پ' to intArrayOf(0xFB56, 0xFB57, 0xFB58, 0xFB59),
        'چ' to intArrayOf(0xFB7A, 0xFB7B, 0xFB7C, 0xFB7D),
        'ژ' to intArrayOf(0xFB8A, 0xFB8B),
        'ک' to intArrayOf(0xFB8E, 0xFB8F, 0xFB90, 0xFB91),
        'گ' to intArrayOf(0xFB92, 0xFB93, 0xFB94, 0xFB95),
        'ی' to intArrayOf(0xFBFC, 0xFBFD, 0xFBFE, 0xFBFF),
    )

    // lam + alef ligatures: alef variant -> [isolated, final]
    private val LAM_ALEF: Map<Char, IntArray> = mapOf(
        'آ' to intArrayOf(0xFEF5, 0xFEF6),
        'أ' to intArrayOf(0xFEF7, 0xFEF8),
        'إ' to intArrayOf(0xFEF9, 0xFEFA),
        'ا' to intArrayOf(0xFEFB, 0xFEFC),
    )

    private const val TATWEEL = 'ـ'
    private const val LAM = 'ل'

    /** Harakat / small marks: don't break or create joins. */
    private fun transparent(c: Char) = c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ'

    private fun joinsNext(c: Char) = c == TATWEEL || (FORMS[c]?.size ?: 0) == 4
    private fun joinsPrev(c: Char) = c == TATWEEL || (FORMS[c]?.size ?: 0) >= 2

    fun hasArabic(s: String) = s.any { it in FORMS || it == TATWEEL }

    fun shape(input: String): String {
        if (!hasArabic(input)) return input
        val s = input
        val out = StringBuilder(s.length)

        fun prevIndex(i: Int): Int {
            var j = i - 1
            while (j >= 0 && transparent(s[j])) j--
            return j
        }
        fun nextIndex(i: Int): Int {
            var j = i + 1
            while (j < s.length && transparent(s[j])) j++
            return j
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]
            val forms = FORMS[c]
            if (forms == null) {
                out.append(c)
                i++
                continue
            }
            val p = prevIndex(i)
            val n = nextIndex(i)
            val connectPrev = p >= 0 && joinsNext(s[p]) && joinsPrev(c)

            // lam + alef ligature
            if (c == LAM && n < s.length && LAM_ALEF.containsKey(s[n])) {
                val lig = LAM_ALEF.getValue(s[n])
                out.append((if (connectPrev) lig[1] else lig[0]).toChar())
                // keep any marks that were between lam and alef
                for (k in i + 1 until n) out.append(s[k])
                i = n + 1
                continue
            }

            val connectNext = n < s.length && joinsNext(c) && joinsPrev(s[n])
            val code = when {
                forms.size == 1 -> forms[0]
                connectPrev && connectNext && forms.size == 4 -> forms[3]
                connectPrev -> forms[1]
                connectNext && forms.size == 4 -> forms[2]
                else -> forms[0]
            }
            out.append(code.toChar())
            i++
        }
        return out.toString()
    }
}
