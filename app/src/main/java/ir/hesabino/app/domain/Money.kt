package ir.hesabino.app.domain

object Money {
    fun formatToman(rial: Long): String {
        return "%,d تومان".format(rial / 10)
    }
}
