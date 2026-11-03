package com.boothly.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/** Stores everything in one JSON file + a photos folder inside the app's private storage. */
object Repo {
    private lateinit var app: Context
    private val lock = Any()
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    fun init(ctx: Context) {
        app = ctx.applicationContext
        _data.value = load()
        cleanupOrphans()
    }

    fun photosDir(): File = File(app.filesDir, "photos").apply { mkdirs() }
    fun photoFile(name: String): File = File(photosDir(), name)

    private fun dataFile() = File(app.filesDir, "data.json")
    private fun backupFile() = File(app.filesDir, "data.json.bak")

    private fun load(): AppData {
        for (f in listOf(dataFile(), backupFile())) {
            try {
                if (f.exists()) return appDataFromJson(JSONObject(f.readText()))
            } catch (_: Exception) {
            }
        }
        return AppData()
    }

    private fun commit(d: AppData) = synchronized(lock) {
        _data.value = d
        val f = dataFile()
        val tmp = File(app.filesDir, "data.json.tmp")
        tmp.writeText(d.toJson().toString())
        if (f.exists()) f.copyTo(backupFile(), overwrite = true)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    fun supplier(id: String): Supplier? = _data.value.suppliers.find { it.id == id }
    fun product(id: String): Product? = _data.value.products.find { it.id == id }

    fun upsertSupplier(s: Supplier) = synchronized(lock) {
        val d = _data.value
        val list = if (d.suppliers.any { it.id == s.id }) d.suppliers.map { if (it.id == s.id) s else it } else d.suppliers + s
        commit(d.copy(suppliers = list))
    }

    fun deleteSupplier(id: String) = synchronized(lock) {
        val d = _data.value
        commit(d.copy(suppliers = d.suppliers.filter { it.id != id }, products = d.products.filter { it.supplierId != id }))
    }

    fun upsertProduct(p: Product) = synchronized(lock) {
        val d = _data.value
        val list = if (d.products.any { it.id == p.id }) d.products.map { if (it.id == p.id) p else it } else d.products + p
        commit(d.copy(products = list))
    }

    fun deleteProduct(id: String) = synchronized(lock) {
        val d = _data.value
        commit(d.copy(products = d.products.filter { it.id != id }))
    }

    /** Merge a restored backup: records with the same id are replaced, everything else is kept. */
    fun merge(other: AppData) = synchronized(lock) {
        val d = _data.value
        val sup = LinkedHashMap<String, Supplier>()
        d.suppliers.forEach { sup[it.id] = it }
        other.suppliers.forEach { sup[it.id] = it }
        val prod = LinkedHashMap<String, Product>()
        d.products.forEach { prod[it.id] = it }
        other.products.forEach { prod[it.id] = it }
        commit(AppData(sup.values.toList(), prod.values.toList()))
    }

    fun allPhotoNames(d: AppData = _data.value): List<String> =
        d.suppliers.flatMap { it.cardImages } + d.products.flatMap { it.photos }

    /** Delete photo files no longer used by any record (older than a day, so in-progress edits are safe). */
    private fun cleanupOrphans() {
        try {
            val used = allPhotoNames().toSet()
            val cutoff = System.currentTimeMillis() - 24L * 3600 * 1000
            photosDir().listFiles()?.forEach { f ->
                if (f.name !in used && f.lastModified() < cutoff) f.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun prefs() = app.getSharedPreferences("boothly", Context.MODE_PRIVATE)
    var azureKey: String
        get() = prefs().getString("azureKey", "") ?: ""
        set(v) {
            prefs().edit().putString("azureKey", v.trim()).apply()
        }
    var azureRegion: String
        get() = prefs().getString("azureRegion", "eastasia") ?: "eastasia"
        set(v) {
            prefs().edit().putString("azureRegion", v.trim().lowercase()).apply()
        }
    var voiceLanguage: String
        get() = prefs().getString("voiceLang", "en-US") ?: "en-US"
        set(v) {
            prefs().edit().putString("voiceLang", v).apply()
        }

    var lastCurrency: String
        get() = prefs().getString("currency", "USD") ?: "USD"
        set(v) {
            prefs().edit().putString("currency", v).apply()
        }
}
