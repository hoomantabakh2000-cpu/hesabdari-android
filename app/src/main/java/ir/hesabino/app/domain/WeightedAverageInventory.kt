package ir.hesabino.app.domain

data class Money(val rial: Long) {
    operator fun plus(other: Money) = Money(rial + other.rial)
    operator fun minus(other: Money) = Money(rial - other.rial)
}

enum class InventoryTransactionType {
    OPENING_BALANCE,
    PURCHASE,
    SALE,
    SALE_RETURN,
    PURCHASE_RETURN
}

data class InventoryTransaction(
    val type: InventoryTransactionType,
    val quantity: Long,
    val unitCostRial: Long
)

data class InventoryState(
    val quantity: Long,
    val valueRial: Long,
    val averageCostRial: Long
)

object WeightedAverageInventory {
    fun calculate(
        transactions: List<InventoryTransaction>,
        allowNegative: Boolean = false
    ): InventoryState {
        var quantity = 0L
        var value = 0L

        transactions.forEach { tx ->
            require(tx.quantity > 0) { "تعداد تراکنش باید بیشتر از صفر باشد." }
            require(tx.unitCostRial >= 0) { "بهای کالا نمی‌تواند منفی باشد." }

            when (tx.type) {
                InventoryTransactionType.OPENING_BALANCE,
                InventoryTransactionType.PURCHASE -> {
                    quantity = Math.addExact(quantity, tx.quantity)
                    value = Math.addExact(value, Math.multiplyExact(tx.quantity, tx.unitCostRial))
                }

                InventoryTransactionType.SALE -> {
                    require(allowNegative || tx.quantity <= quantity) {
                        "موجودی برای این فروش کافی نیست."
                    }
                    val cost = averageCost(quantity, value)
                    quantity -= tx.quantity
                    value -= Math.multiplyExact(tx.quantity, cost)
                    if (quantity == 0L) value = 0L
                }

                InventoryTransactionType.SALE_RETURN -> {
                    quantity = Math.addExact(quantity, tx.quantity)
                    value = Math.addExact(value, Math.multiplyExact(tx.quantity, tx.unitCostRial))
                }

                InventoryTransactionType.PURCHASE_RETURN -> {
                    require(allowNegative || tx.quantity <= quantity) {
                        "موجودی برای برگشت خرید کافی نیست."
                    }
                    quantity -= tx.quantity
                    value -= Math.multiplyExact(tx.quantity, tx.unitCostRial)
                    if (quantity == 0L) value = 0L
                }
            }
        }

        return InventoryState(quantity, value, averageCost(quantity, value))
    }

    private fun averageCost(quantity: Long, value: Long): Long =
        if (quantity == 0L) 0L else (value + quantity / 2L) / quantity
}
