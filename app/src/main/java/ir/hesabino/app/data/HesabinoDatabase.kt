package ir.hesabino.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HesabinoDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "hesabino.db",
    null,
    3
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createProductsTable(db)
        createInventoryTransactionsTable(db)
        createIndexes(db)
        createSuppliersAndPurchases(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createInventoryTransactionsTable(db)
            createInventoryIndexes(db)

            // Migrate legacy opening stock into the transaction ledger so the ledger
            // becomes the source of truth without losing existing user data.
            db.execSQL(
                """
                INSERT INTO inventory_transactions
                    (product_id, type, quantity, unit_cost_rial, reference_type, reference_id, created_at)
                SELECT
                    id, 'OPENING_BALANCE', stock, purchase_price_rial, 'OPENING_BALANCE', id, created_at
                FROM products
                WHERE stock > 0
                """.trimIndent()
            )
            db.execSQL("UPDATE products SET stock = 0 WHERE stock > 0")
        }
        if (oldVersion < 3) createSuppliersAndPurchases(db)
    }

    private fun createProductsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS products (
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
            """.trimIndent()
        )
    }

    private fun createInventoryTransactionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS inventory_transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                product_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                quantity INTEGER NOT NULL CHECK(quantity > 0),
                unit_cost_rial INTEGER NOT NULL CHECK(unit_cost_rial >= 0),
                reference_type TEXT,
                reference_id INTEGER,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE RESTRICT
            )
            """.trimIndent()
        )
    }

    private fun createSuppliersAndPurchases(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS suppliers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                phone TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_invoices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                supplier_id INTEGER NOT NULL,
                invoice_number TEXT NOT NULL DEFAULT '',
                total_rial INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(supplier_id) REFERENCES suppliers(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS purchase_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                invoice_id INTEGER NOT NULL,
                product_id INTEGER NOT NULL,
                quantity INTEGER NOT NULL CHECK(quantity > 0),
                unit_cost_rial INTEGER NOT NULL CHECK(unit_cost_rial > 0),
                FOREIGN KEY(invoice_id) REFERENCES purchase_invoices(id) ON DELETE CASCADE,
                FOREIGN KEY(product_id) REFERENCES products(id) ON DELETE RESTRICT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_purchase_supplier ON purchase_invoices(supplier_id, created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_purchase_item_invoice ON purchase_items(invoice_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_purchase_item_product ON purchase_items(product_id)")
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_barcode ON products(barcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_products_name ON products(name)")
        createInventoryIndexes(db)
    }

    private fun createInventoryIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_product ON inventory_transactions(product_id, created_at, id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_reference ON inventory_transactions(reference_type, reference_id)")
    }
}

class ProductRepository(context: Context) {
    private val db = HesabinoDatabase(context)
    private val inventoryLedger = InventoryTransactionRepository(db)

    fun getAll(): List<Product> {
        val result = mutableListOf<Product>()
        db.readableDatabase.query(
            "products",
            null,
            null,
            null,
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { c ->
            val id = c.getColumnIndexOrThrow("id")
            val name = c.getColumnIndexOrThrow("name")
            val barcode = c.getColumnIndexOrThrow("barcode")
            val unit = c.getColumnIndexOrThrow("unit")
            val purchase = c.getColumnIndexOrThrow("purchase_price_rial")
            val sale = c.getColumnIndexOrThrow("sale_price_rial")
            val minStock = c.getColumnIndexOrThrow("min_stock")
            val created = c.getColumnIndexOrThrow("created_at")

            while (c.moveToNext()) {
                val productId = c.getLong(id)
                result += Product(
                    id = productId,
                    name = c.getString(name),
                    barcode = c.getString(barcode),
                    unit = c.getString(unit),
                    purchasePriceRial = c.getLong(purchase),
                    salePriceRial = c.getLong(sale),
                    stock = inventoryLedger.getCurrentState(productId).quantity,
                    minStock = c.getLong(minStock),
                    createdAt = c.getLong(created)
                )
            }
        }
        return result
    }

    fun insert(product: Product): Long {
        require(product.name.isNotBlank()) { "نام کالا الزامی است." }
        require(product.stock >= 0) { "موجودی اولیه نمی‌تواند منفی باشد." }
        if (product.stock > 0) {
            require(product.purchasePriceRial > 0) { "برای موجودی اولیه، بهای خرید الزامی است." }
        }

        val database = db.writableDatabase
        database.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("name", product.name.trim())
                put("barcode", product.barcode.trim())
                put("unit", product.unit.trim().ifBlank { "عدد" })
                put("purchase_price_rial", product.purchasePriceRial)
                put("sale_price_rial", product.salePriceRial)
                put("stock", 0L)
                put("min_stock", product.minStock)
                put("created_at", product.createdAt)
            }
            val productId = database.insertOrThrow("products", null, values)

            if (product.stock > 0) {
                val tx = ContentValues().apply {
                    put("product_id", productId)
                    put("type", "OPENING_BALANCE")
                    put("quantity", product.stock)
                    put("unit_cost_rial", product.purchasePriceRial)
                    put("reference_type", "OPENING_BALANCE")
                    put("reference_id", productId)
                    put("created_at", product.createdAt)
                }
                database.insertOrThrow("inventory_transactions", null, tx)
            }

            database.setTransactionSuccessful()
            return productId
        } finally {
            database.endTransaction()
        }
    }

    fun update(product: Product) {
        require(product.id > 0) { "شناسه کالا نامعتبر است." }
        require(product.name.isNotBlank()) { "نام کالا الزامی است." }
        require(product.purchasePriceRial >= 0 && product.salePriceRial >= 0) { "قیمت نمی‌تواند منفی باشد." }
        require(product.minStock >= 0) { "حداقل موجودی نمی‌تواند منفی باشد." }
        val values = ContentValues().apply {
            put("name", product.name.trim())
            put("barcode", product.barcode.trim())
            put("unit", product.unit.trim().ifBlank { "عدد" })
            put("purchase_price_rial", product.purchasePriceRial)
            put("sale_price_rial", product.salePriceRial)
            put("min_stock", product.minStock)
        }
        db.writableDatabase.update("products", values, "id = ?", arrayOf(product.id.toString()))
    }

    fun addPurchase(productId: Long, quantity: Long, unitCostRial: Long) {
        require(quantity > 0) { "تعداد خرید باید بیشتر از صفر باشد." }
        require(unitCostRial > 0) { "قیمت خرید باید بیشتر از صفر باشد." }
        inventoryLedger.add(productId, "PURCHASE", quantity, unitCostRial, "MANUAL_PURCHASE")
        val values = ContentValues().apply { put("purchase_price_rial", unitCostRial) }
        db.writableDatabase.update("products", values, "id = ?", arrayOf(productId.toString()))
    }

    fun getInventoryValueRial(): Long = getAll().sumOf { product ->
        inventoryLedger.getCurrentState(product.id).valueRial
    }

    fun delete(id: Long) {
        val count = db.readableDatabase.query(
            "inventory_transactions",
            arrayOf("id"),
            "product_id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { it.count }

        require(count == 0) { "کالایی که گردش انبار دارد قابل حذف نیست." }
        db.writableDatabase.delete("products", "id = ?", arrayOf(id.toString()))
    }

    fun close() = db.close()
}

class InventoryTransactionRepository(private val db: HesabinoDatabase) {
    constructor(context: Context) : this(HesabinoDatabase(context))

    fun add(
        productId: Long,
        type: String,
        quantity: Long,
        unitCostRial: Long,
        referenceType: String? = null,
        referenceId: Long? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        require(quantity > 0) { "تعداد تراکنش باید بیشتر از صفر باشد." }
        require(unitCostRial >= 0) { "بهای کالا نمی‌تواند منفی باشد." }

        val values = ContentValues().apply {
            put("product_id", productId)
            put("type", type)
            put("quantity", quantity)
            put("unit_cost_rial", unitCostRial)
            if (referenceType == null) putNull("reference_type") else put("reference_type", referenceType)
            if (referenceId == null) putNull("reference_id") else put("reference_id", referenceId)
            put("created_at", createdAt)
        }
        return db.writableDatabase.insertOrThrow("inventory_transactions", null, values)
    }

    fun getCurrentState(productId: Long): InventoryLedgerState {
        var quantity = 0L
        var value = 0L

        db.readableDatabase.query(
            "inventory_transactions",
            arrayOf("type", "quantity", "unit_cost_rial"),
            "product_id = ?",
            arrayOf(productId.toString()),
            null,
            null,
            "created_at ASC, id ASC"
        ).use { c ->
            val type = c.getColumnIndexOrThrow("type")
            val qty = c.getColumnIndexOrThrow("quantity")
            val cost = c.getColumnIndexOrThrow("unit_cost_rial")

            while (c.moveToNext()) {
                val q = c.getLong(qty)
                val unitCost = c.getLong(cost)
                when (c.getString(type)) {
                    "OPENING_BALANCE", "PURCHASE", "SALE_RETURN" -> {
                        quantity += q
                        value += q * unitCost
                    }
                    "SALE" -> {
                        require(q <= quantity) { "موجودی برای این فروش کافی نیست." }
                        val average = if (quantity == 0L) 0L else (value + quantity / 2L) / quantity
                        quantity -= q
                        value -= q * average
                        if (quantity == 0L) value = 0L
                    }
                    "PURCHASE_RETURN" -> {
                        require(q <= quantity) { "موجودی برای برگشت خرید کافی نیست." }
                        quantity -= q
                        value -= q * unitCost
                        if (quantity == 0L) value = 0L
                    }
                    else -> error("نوع تراکنش انبار ناشناخته است.")
                }
            }
        }

        return InventoryLedgerState(
            quantity = quantity,
            valueRial = value,
            averageCostRial = if (quantity == 0L) 0L else (value + quantity / 2L) / quantity
        )
    }
}

data class InventoryLedgerState(
    val quantity: Long,
    val valueRial: Long,
    val averageCostRial: Long
)
