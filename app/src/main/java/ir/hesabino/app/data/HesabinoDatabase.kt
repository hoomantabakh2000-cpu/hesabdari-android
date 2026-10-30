package ir.hesabino.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HesabinoDatabase(context: Context) : SQLiteOpenHelper(context.applicationContext, "hesabino.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                barcode TEXT NOT NULL DEFAULT '',
                unit TEXT NOT NULL DEFAULT 'عدد',
                purchase_price_rial INTEGER NOT NULL DEFAULT 0,
                sale_price_rial INTEGER NOT NULL DEFAULT 0,
                stock INTEGER NOT NULL DEFAULT 0,
                min_stock INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_products_barcode ON products(barcode)")
        db.execSQL("CREATE INDEX idx_products_name ON products(name)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

class ProductRepository(context: Context) {
    private val db = HesabinoDatabase(context)

    fun getAll(): List<Product> {
        val result = mutableListOf<Product>()
        db.readableDatabase.query("products", null, null, null, null, null, "name COLLATE NOCASE ASC").use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val name = c.getColumnIndexOrThrow("name")
            val barcode = c.getColumnIndexOrThrow("barcode")
            val unit = c.getColumnIndexOrThrow("unit")
            val purchase = c.getColumnIndexOrThrow("purchase_price_rial")
            val sale = c.getColumnIndexOrThrow("sale_price_rial")
            val stock = c.getColumnIndexOrThrow("stock")
            val minStock = c.getColumnIndexOrThrow("min_stock")
            val created = c.getColumnIndexOrThrow("created_at")
            while (c.moveToNext()) result += Product(
                c.getLong(id), c.getString(name), c.getString(barcode), c.getString(unit),
                c.getLong(purchase), c.getLong(sale), c.getLong(stock), c.getLong(minStock), c.getLong(created)
            )
        }
        return result
    }

    fun insert(product: Product): Long {
        val values = ContentValues().apply {
            put("name", product.name.trim())
            put("barcode", product.barcode.trim())
            put("unit", product.unit.trim().ifBlank { "عدد" })
            put("purchase_price_rial", product.purchasePriceRial)
            put("sale_price_rial", product.salePriceRial)
            put("stock", product.stock)
            put("min_stock", product.minStock)
            put("created_at", product.createdAt)
        }
        return db.writableDatabase.insertOrThrow("products", null, values)
    }

    fun delete(id: Long) {
        db.writableDatabase.delete("products", "id = ?", arrayOf(id.toString()))
    }

    fun close() = db.close()
}
