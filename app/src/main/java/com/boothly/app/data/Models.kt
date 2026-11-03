package com.boothly.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Supplier(
    val id: String = UUID.randomUUID().toString(),
    val company: String = "",
    val contactName: String = "",
    val title: String = "",
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String = "",
    val wechat: String = "",
    val whatsapp: String = "",
    val address: String = "",
    val booth: String = "",
    val notes: String = "",
    val cardImages: List<String> = emptyList(),
    val ocrText: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayName: String
        get() = company.ifBlank { contactName.ifBlank { "Unnamed supplier" } }

    fun matches(q: String): Boolean {
        if (q.isBlank()) return true
        return listOf(company, contactName, title, website, wechat, whatsapp, address, booth, notes)
            .plus(phones).plus(emails)
            .any { it.contains(q, ignoreCase = true) }
    }
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val supplierId: String,
    val name: String = "",
    val price: String = "",
    val currency: String = "USD",
    val moq: String = "",
    val notes: String = "",
    val photos: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
) {
    val priceLabel: String
        get() = if (price.isBlank()) "" else "$currency $price"

    val displayName: String
        get() = name.ifBlank { "Product" }

    fun matches(q: String): Boolean {
        if (q.isBlank()) return true
        return listOf(name, price, moq, notes).any { it.contains(q, ignoreCase = true) }
    }
}

data class AppData(
    val suppliers: List<Supplier> = emptyList(),
    val products: List<Product> = emptyList(),
)

private fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

private fun List<String>.toJsonArray(): JSONArray = JSONArray().also { a -> forEach { a.put(it) } }

fun Supplier.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("company", company)
    put("contactName", contactName)
    put("title", title)
    put("phones", phones.toJsonArray())
    put("emails", emails.toJsonArray())
    put("website", website)
    put("wechat", wechat)
    put("whatsapp", whatsapp)
    put("address", address)
    put("booth", booth)
    put("notes", notes)
    put("cardImages", cardImages.toJsonArray())
    put("ocrText", ocrText)
    put("createdAt", createdAt)
}

fun supplierFromJson(o: JSONObject): Supplier = Supplier(
    id = o.getString("id"),
    company = o.optString("company"),
    contactName = o.optString("contactName"),
    title = o.optString("title"),
    phones = o.optJSONArray("phones").strings(),
    emails = o.optJSONArray("emails").strings(),
    website = o.optString("website"),
    wechat = o.optString("wechat"),
    whatsapp = o.optString("whatsapp"),
    address = o.optString("address"),
    booth = o.optString("booth"),
    notes = o.optString("notes"),
    cardImages = o.optJSONArray("cardImages").strings(),
    ocrText = o.optString("ocrText"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
)

fun Product.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("supplierId", supplierId)
    put("name", name)
    put("price", price)
    put("currency", currency)
    put("moq", moq)
    put("notes", notes)
    put("photos", photos.toJsonArray())
    put("createdAt", createdAt)
}

fun productFromJson(o: JSONObject): Product = Product(
    id = o.getString("id"),
    supplierId = o.getString("supplierId"),
    name = o.optString("name"),
    price = o.optString("price"),
    currency = o.optString("currency", "USD"),
    moq = o.optString("moq"),
    notes = o.optString("notes"),
    photos = o.optJSONArray("photos").strings(),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
)

fun AppData.toJson(): JSONObject = JSONObject().apply {
    put("app", "Boothly")
    put("version", 1)
    put("suppliers", JSONArray().also { a -> suppliers.forEach { a.put(it.toJson()) } })
    put("products", JSONArray().also { a -> products.forEach { a.put(it.toJson()) } })
}

fun appDataFromJson(o: JSONObject): AppData {
    val s = o.optJSONArray("suppliers")
    val p = o.optJSONArray("products")
    return AppData(
        suppliers = if (s == null) emptyList() else (0 until s.length()).map { supplierFromJson(s.getJSONObject(it)) },
        products = if (p == null) emptyList() else (0 until p.length()).map { productFromJson(p.getJSONObject(it)) },
    )
}
