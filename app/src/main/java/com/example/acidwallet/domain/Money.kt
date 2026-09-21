package com.example.acidwallet.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Работа с деньгами.
 *
 * Главное правило финансового ПО: деньги никогда не хранятся в Double.
 * Только целые числа (копейки) — иначе округление «съест» копейки,
 * и инвариант баланса перестанет сходиться.
 */
object Money {

    private val ruSymbols: DecimalFormatSymbols =
        DecimalFormatSymbols(Locale("ru", "RU"))

    /** «1234.56» ₽ → «1 234,56 ₽». */
    fun format(cents: Long, currency: String = "₽", withSign: Boolean = false): String {
        val format = DecimalFormat(if (withSign) "+#,##0.00;−#,##0.00" else "#,##0.00", ruSymbols)
        val amount = BigDecimal.valueOf(cents).movePointLeft(2)
        val text = format.format(amount)
        return "$text $currency"
    }

    /** Короткая подпись для операции: «+500,00 ₽» / «−500,00 ₽». */
    fun formatSigned(cents: Long, currency: String = "₽"): String {
        val sign = if (cents < 0) "−" else "+"
        return sign + format(kotlin.math.abs(cents), currency)
    }

    /** «Оставить 2 знака» — для ввода пользователя. */
    fun formatPlain(cents: Long): String =
        BigDecimal.valueOf(cents).movePointLeft(2)
            .setScale(2, RoundingMode.HALF_UP)
            .toPlainString()

    /**
     * Разбор пользовательского ввода: «1 234,5», «1234.5», «1234» → копейки.
     * Возвращает null, если ввод некорректен.
     */
    fun parseToCents(input: String): Long? {
        val cleaned = input
            .replace("\u00A0", "")
            .replace(" ", "")
            .replace("₽", "")
            .replace(',', '.')
            .trim()
        if (cleaned.isEmpty()) return null
        val decimal = cleaned.toBigDecimalOrNull() ?: return null
        if (decimal.scale() > 2) return null
        return decimal.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }
}
