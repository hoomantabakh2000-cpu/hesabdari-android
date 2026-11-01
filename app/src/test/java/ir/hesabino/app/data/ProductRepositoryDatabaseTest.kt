package ir.hesabino.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductRepositoryDatabaseTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("hesabino.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("hesabino.db")
    }

    @Test
    fun insertUpdateStockAndReopenPreserveProductAndCategory() {
        val repository = ProductRepository(context)
        val id = repository.insert(Product(
            name = "کرم مرطوب‌کننده",
            barcode = "۱۲۳۴۵",
            category = "مراقبتی",
            purchasePriceRial = 250_000,
            salePriceRial = 390_000,
            stock = 3,
            minStock = 1
        ))
        assertNotEquals(0L, id)
        var product = repository.getAll().single()
        assertEquals("مراقبتی", product.category)
        assertEquals("کرم مرطوب‌کننده", product.name)
        assertEquals(3L, product.stock)
        assertEquals(750_000L, repository.getInventoryValueRial())

        repository.update(product.copy(name = "کرم آبرسان", category = "آرایشی", salePriceRial = 420_000))
        repository.setStock(id, 5)
        repository.close()

        val reopened = ProductRepository(context)
        product = reopened.getAll().single()
        assertEquals("کرم آبرسان", product.name)
        assertEquals("آرایشی", product.category)
        assertEquals(5L, product.stock)
        assertEquals(1_250_000L, reopened.getInventoryValueRial())
        reopened.close()
    }

    @Test
    fun stockAdjustmentOutCannotCreateNegativeStock() {
        val repository = ProductRepository(context)
        val id = repository.insert(Product(
            name = "شامپو",
            purchasePriceRial = 100_000,
            stock = 2
        ))
        assertThrows(IllegalArgumentException::class.java) {
            repository.setStock(id, -1)
        }
        assertEquals(2L, repository.getAll().single().stock)
        repository.close()
    }

    @Test
    fun versionThreeDatabaseMigrationAddsDefaultCategoryWithoutLosingProducts() {
        val legacy = LegacyVersionThreeDatabase(context)
        legacy.writableDatabase
        legacy.close()

        val upgraded = HesabinoDatabase(context)
        val cursor = upgraded.readableDatabase.rawQuery(
            "SELECT name, category, stock FROM products WHERE id = 41",
            null
        )
        cursor.use {
            assertEquals(true, it.moveToFirst())
            assertEquals("کالای قدیمی", it.getString(0))
            assertEquals("سایر", it.getString(1))
            assertEquals(0L, it.getLong(2))
        }
        upgraded.close()
    }

    @Test
    fun operationsOnMissingProductsAreRejected() {
        val repository = ProductRepository(context)
        assertThrows(IllegalArgumentException::class.java) {
            repository.setStock(999L, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.update(Product(id = 999L, name = "کالای حذف‌شده"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.delete(999L)
        }
        repository.close()
    }

    @Test
    fun reducingAndIncreasingStockKeepsWeightedInventoryValueCorrect() {
        val repository = ProductRepository(context)
        val id = repository.insert(Product(name = "شامپو", purchasePriceRial = 80_000, stock = 6))

        repository.setStock(id, 4)
        assertEquals(4L, repository.getAll().single().stock)
        assertEquals(320_000L, repository.getInventoryValueRial())

        repository.setStock(id, 5)
        assertEquals(5L, repository.getAll().single().stock)
        assertEquals(400_000L, repository.getInventoryValueRial())

        repository.setStock(id, 0)
        assertEquals(0L, repository.getAll().single().stock)
        assertEquals(0L, repository.getInventoryValueRial())
        repository.close()
    }

    @Test
    fun productCanBeDeletedOnlyBeforeItHasInventoryMovements() {
        val repository = ProductRepository(context)
        val emptyId = repository.insert(Product(name = "کالای بدون موجودی"))
        repository.delete(emptyId)
        assertEquals(0, repository.getAll().size)

        val stockedId = repository.insert(Product(name = "کالای دارای موجودی", purchasePriceRial = 25_000, stock = 2))
        assertThrows(IllegalArgumentException::class.java) {
            repository.delete(stockedId)
        }
        assertEquals(2L, repository.getAll().single().stock)
        repository.close()
    }
    @Test
    fun insertRejectsNegativePricesAndMinimumStock() {
        val repository = ProductRepository(context)
        assertThrows(IllegalArgumentException::class.java) {
            repository.insert(Product(name = "کالای نامعتبر", purchasePriceRial = -1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.insert(Product(name = "کالای نامعتبر", minStock = -1))
        }
        assertEquals(0, repository.getAll().size)
        repository.close()
    }

    private class LegacyVersionThreeDatabase(context: Context) :
        SQLiteOpenHelper(context, "hesabino.db", null, 3) {
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
            db.execSQL("""
                CREATE TABLE inventory_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    product_id INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    quantity INTEGER NOT NULL CHECK(quantity > 0),
                    unit_cost_rial INTEGER NOT NULL CHECK(unit_cost_rial >= 0),
                    reference_type TEXT,
                    reference_id INTEGER,
                    created_at INTEGER NOT NULL
                )
            """.trimIndent())
            db.execSQL("""
                INSERT INTO products(id, name, stock, created_at)
                VALUES (41, 'کالای قدیمی', 0, 1000)
            """.trimIndent())
            db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE purchase_invoices (id INTEGER PRIMARY KEY AUTOINCREMENT, supplier_id INTEGER NOT NULL, invoice_number TEXT NOT NULL DEFAULT '', total_rial INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE purchase_items (id INTEGER PRIMARY KEY AUTOINCREMENT, invoice_id INTEGER NOT NULL, product_id INTEGER NOT NULL, quantity INTEGER NOT NULL, unit_cost_rial INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
}
