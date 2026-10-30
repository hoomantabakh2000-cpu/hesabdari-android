package ir.hesabino.app.data

data class Product(
    val id: Long = 0L,
    val name: String,
    val barcode: String = "",
    val unit: String = "عدد",
    val purchasePriceRial: Long = 0L,
    val salePriceRial: Long = 0L,
    val stock: Long = 0L,
    val minStock: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
