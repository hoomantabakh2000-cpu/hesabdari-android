package ir.hesabino.app.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PurchaseRepositoryDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: HesabinoDatabase
    private lateinit var suppliers: SupplierRepository
    private lateinit var purchases: PurchaseRepository
    private lateinit var products: ProductRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("hesabino.db")
        db = HesabinoDatabase(context)
        suppliers = SupplierRepository(db)
        purchases = PurchaseRepository(db)
        products = ProductRepository(context)
    }

    @After
    fun tearDown() {
        products.close()
        db.close()
        context.deleteDatabase("hesabino.db")
    }

    @Test
    fun savesInvoiceUpdatesStockAndKeepsHistoricalProductName() {
        val supplierId = suppliers.findOrCreate("تأمین‌کننده نمونه", "09120000000")
        val productId = products.insert(Product(name = "شامپو اولیه", purchasePriceRial = 100_000, stock = 1))

        val invoiceId = purchases.saveInvoice(
            supplierId,
            "A-102",
            listOf(PurchaseLine(productId, 3, 120_000))
        )

        assertEquals(4L, products.getAll().single().stock)
        assertEquals(460_000L, products.getInventoryValueRial())
        assertEquals(360_000L, purchases.getInvoices(supplierId).single().totalRial)
        products.update(products.getAll().single().copy(name = "شامپو با نام جدید"))
        assertEquals("شامپو اولیه", purchases.getInvoiceItems(invoiceId).single().productName)
        assertEquals(3L, purchases.getInvoiceItems(invoiceId).single().quantity)
    }

    @Test
    fun invalidLinesAreRejectedBeforeAnyInvoiceIsSaved() {
        val supplierId = suppliers.findOrCreate("تأمین‌کننده")
        val productId = products.insert(Product(name = "کالا", purchasePriceRial = 50_000))
        assertThrows(IllegalArgumentException::class.java) {
            purchases.saveInvoice(supplierId, "bad", listOf(PurchaseLine(productId, 0, 10)))
        }
        assertEquals(0, purchases.getInvoices(supplierId).size)
        assertEquals(0L, products.getAll().single().stock)
    }

    @Test
    fun invoiceTotalOverflowRollsBackInvoiceAndStock() {
        val supplierId = suppliers.findOrCreate("تأمین‌کننده")
        val productId = products.insert(Product(name = "کالای گران", purchasePriceRial = 1))
        assertThrows(ArithmeticException::class.java) {
            purchases.saveInvoice(supplierId, "overflow", listOf(PurchaseLine(productId, Long.MAX_VALUE, 2)))
        }
        assertEquals(0, purchases.getInvoices(supplierId).size)
        assertEquals(0L, products.getAll().single().stock)
    }

    @Test
    fun unknownProductRollsBackEntireInvoice() {
        val supplierId = suppliers.findOrCreate("تأمین‌کننده")
        assertThrows(IllegalArgumentException::class.java) {
            purchases.saveInvoice(supplierId, "missing", listOf(PurchaseLine(9999, 1, 10)))
        }
        assertEquals(0, purchases.getInvoices(supplierId).size)
    }
}
