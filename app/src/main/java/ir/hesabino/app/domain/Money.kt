package ir.hesabino.app.domain

object MoneyFormatter {
    fun formatToman(rial: Long): String {
        return "%,d تومان".format(rial / 10)
    }
}
