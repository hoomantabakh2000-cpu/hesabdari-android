package ir.hesabino.app.domain

import ir.hesabino.app.data.Product

object InventoryProductFilter {
    fun filter(
        products: List<Product>,
        search: String = "",
        category: String = "همه کالاها",
        stockFilter: String = "همه"
    ): List<Product> {
        val query = search.trim()
        val barcodeQuery = normalizeDigits(query)
        return products.filter { product ->
            val nameMatches = query.isBlank() || product.name.contains(query, ignoreCase = true)
            val barcodeMatches = barcodeQuery.isNotBlank() &&
                normalizeDigits(product.barcode).contains(barcodeQuery)
            val categoryMatches = category == "همه کالاها" || product.category == category
            val stockMatches = when (stockFilter) {
                "کم‌موجود" -> product.stock > 0L && product.stock <= product.minStock
                "ناموجود" -> product.stock == 0L
                else -> true
            }
            (nameMatches || barcodeMatches) && categoryMatches && stockMatches
        }
    }

    private fun normalizeDigits(value: String): String = value.mapNotNull { character ->
        when (character) {
            in '۰'..'۹' -> ('0'.code + (character.code - '۰'.code)).toChar()
            in '٠'..'٩' -> ('0'.code + (character.code - '٠'.code)).toChar()
            else -> if (character.isDigit()) character else null
        }
    }.joinToString("")
}
