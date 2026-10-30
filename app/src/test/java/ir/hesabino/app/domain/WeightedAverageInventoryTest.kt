package ir.hesabino.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WeightedAverageInventoryTest {
    @Test
    fun purchaseThenSaleUsesWeightedAverageCost() {
        val state = WeightedAverageInventory.calculate(listOf(
            InventoryTransaction(InventoryTransactionType.PURCHASE, 10, 100_000),
            InventoryTransaction(InventoryTransactionType.PURCHASE, 10, 140_000),
            InventoryTransaction(InventoryTransactionType.SALE, 5, 120_000)
        ))
        assertEquals(15L, state.quantity)
        assertEquals(1_800_000L, state.valueRial)
        assertEquals(120_000L, state.averageCostRial)
    }

    @Test
    fun saleReturnUsesOriginalCostBasis() {
        val state = WeightedAverageInventory.calculate(listOf(
            InventoryTransaction(InventoryTransactionType.PURCHASE, 10, 100_000),
            InventoryTransaction(InventoryTransactionType.SALE, 4, 120_000),
            InventoryTransaction(InventoryTransactionType.SALE_RETURN, 1, 100_000)
        ))
        assertEquals(7L, state.quantity)
        assertEquals(700_000L, state.valueRial)
        assertEquals(100_000L, state.averageCostRial)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeInventoryIsRejectedByDefault() {
        WeightedAverageInventory.calculate(
            listOf(InventoryTransaction(InventoryTransactionType.SALE, 1, 100_000))
        )
    }
}
