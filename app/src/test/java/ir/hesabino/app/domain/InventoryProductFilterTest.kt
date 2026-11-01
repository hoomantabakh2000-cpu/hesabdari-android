package ir.hesabino.app.domain

import ir.hesabino.app.data.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class InventoryProductFilterTest {
    private val products = listOf(
        Product(id = 1, name = "کرم مرطوب‌کننده", barcode = "123456", category = "مراقبتی", stock = 5, minStock = 2),
        Product(id = 2, name = "شامپو", barcode = "۹۸۷۶۵۴", category = "بهداشتی", stock = 1, minStock = 2),
        Product(id = 3, name = "رژ لب", barcode = "٠٠١٢٣٤", category = "آرایشی", stock = 0, minStock = 1)
    )

    @Test
    fun textSearchDoesNotMatchEveryBarcodeWhenQueryHasNoDigits() {
        assertEquals(listOf(1L), InventoryProductFilter.filter(products, search = "کرم").map { it.id })
        assertEquals(listOf(2L), InventoryProductFilter.filter(products, search = "شامپو").map { it.id })
        assertEquals(emptyList<Long>(), InventoryProductFilter.filter(products, search = "محصول ناشناس").map { it.id })
    }

    @Test
    fun barcodeSearchAcceptsPersianArabicAndLatinDigits() {
        assertEquals(listOf(1L), InventoryProductFilter.filter(products, search = "۱۲۳۴۵").map { it.id })
        assertEquals(listOf(2L), InventoryProductFilter.filter(products, search = "٩٨٧٦").map { it.id })
        assertEquals(listOf(3L), InventoryProductFilter.filter(products, search = "0012").map { it.id })
    }

    @Test
    fun categoryAndStockFiltersCanBeCombined() {
        assertEquals(
            listOf(2L),
            InventoryProductFilter.filter(products, category = "بهداشتی", stockFilter = "کم‌موجود").map { it.id }
        )
        assertEquals(
            listOf(3L),
            InventoryProductFilter.filter(products, stockFilter = "ناموجود").map { it.id }
        )
    }
}
