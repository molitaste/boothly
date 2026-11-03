package com.boothly.app.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import com.boothly.app.data.Supplier

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

/** One-tap contact actions for a supplier. */
object Contact {
    private fun start(ctx: Context, intent: Intent) {
        try {
            if (ctx !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(ctx, "No app found for this action")
        } catch (_: SecurityException) {
            toast(ctx, "Not allowed")
        }
    }

    fun dial(ctx: Context, number: String) =
        start(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))

    fun email(ctx: Context, address: String, subject: String = "") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address.trim()))
        if (subject.isNotBlank()) i.putExtra(Intent.EXTRA_SUBJECT, subject)
        start(ctx, i)
    }

    /** WhatsApp needs the number with country code. Chinese mobiles (1xx xxxx xxxx) get +86 automatically. */
    fun whatsappNumber(raw: String): String {
        val t = raw.trim()
        var d = t.filter { it.isDigit() }
        if (!t.startsWith("+") && d.startsWith("00")) d = d.drop(2)
        else if (!t.startsWith("+") && d.length == 11 && d.startsWith("1") && d[1] in '3'..'9') d = "86$d"
        return d
    }

    fun whatsapp(ctx: Context, raw: String) {
        val n = whatsappNumber(raw)
        if (n.length < 7) {
            toast(ctx, "Not a valid WhatsApp number")
            return
        }
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n")))
    }

    /** WeChat can't be opened on a specific contact, so copy the ID and open WeChat → Add Contacts → paste. */
    fun wechat(ctx: Context, id: String) {
        copy(ctx, "WeChat ID", id)
        val launch = ctx.packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (launch != null) {
            toast(ctx, "WeChat ID copied – paste it in WeChat › Add Contacts")
            start(ctx, launch)
        } else {
            toast(ctx, "WeChat ID copied (WeChat not installed)")
        }
    }

    fun web(ctx: Context, url: String) {
        val u = url.trim().let { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    fun map(ctx: Context, address: String) =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address))))

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun saveToPhoneContacts(ctx: Context, s: Supplier) {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, s.contactName.ifBlank { s.company })
            putExtra(ContactsContract.Intents.Insert.COMPANY, s.company)
            putExtra(ContactsContract.Intents.Insert.JOB_TITLE, s.title)
            s.phones.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            s.phones.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE, it) }
            s.phones.getOrNull(2)?.let { putExtra(ContactsContract.Intents.Insert.TERTIARY_PHONE, it) }
            s.emails.getOrNull(0)?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            s.emails.getOrNull(1)?.let { putExtra(ContactsContract.Intents.Insert.SECONDARY_EMAIL, it) }
            if (s.address.isNotBlank()) putExtra(ContactsContract.Intents.Insert.POSTAL, s.address)
            val notes = listOfNotNull(
                s.booth.takeIf { it.isNotBlank() }?.let { "Booth: $it" },
                s.wechat.takeIf { it.isNotBlank() }?.let { "WeChat: $it" },
                s.whatsapp.takeIf { it.isNotBlank() }?.let { "WhatsApp: $it" },
                s.website.takeIf { it.isNotBlank() },
                "Met via Boothly"
            ).joinToString("\n")
            putExtra(ContactsContract.Intents.Insert.NOTES, notes)
        }
        start(ctx, i)
    }

    fun shareText(ctx: Context, s: Supplier) {
        val text = buildString {
            appendLine(s.company)
            if (s.contactName.isNotBlank()) appendLine(listOf(s.contactName, s.title).filter { it.isNotBlank() }.joinToString(" – "))
            if (s.booth.isNotBlank()) appendLine("Booth: ${s.booth}")
            s.phones.forEach { appendLine("Tel: $it") }
            s.emails.forEach { appendLine("Email: $it") }
            if (s.whatsapp.isNotBlank()) appendLine("WhatsApp: ${s.whatsapp}")
            if (s.wechat.isNotBlank()) appendLine("WeChat: ${s.wechat}")
            if (s.website.isNotBlank()) appendLine("Web: ${s.website}")
            if (s.address.isNotBlank()) appendLine("Address: ${s.address}")
        }.trim()
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(ctx, Intent.createChooser(i, "Share supplier"))
    }
}
