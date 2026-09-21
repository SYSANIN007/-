package com.example.acidwallet

import com.example.acidwallet.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Юнит-тесты на работу с деньгами (обычные JVM-тесты, без эмулятора).
 *
 * Деньги в приложении хранятся целыми числами в копейках именно потому,
 * что Double «теряет» копейки: 0.1 + 0.2 != 0.3.
 */
class MoneyTest {

    @Test
    fun parseAcceptsRussianAndDotFormats() {
        assertEquals(150_050L, Money.parseToCents("1500,50"))
        assertEquals(150_050L, Money.parseToCents("1500.50"))
        assertEquals(150_000L, Money.parseToCents("1500"))
        assertEquals(150_000L, Money.parseToCents("1 500"))
        assertEquals(150_000L, Money.parseToCents("1\u00A0500,00"))
        assertEquals(1L, Money.parseToCents("0,01"))
        assertEquals(100_000L, Money.parseToCents("1000 ₽"))
    }

    @Test
    fun parseRejectsGarbageAndTooManyDigits() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("10,555"))   // больше двух знаков после запятой
    }

    @Test
    fun formatPlainIsMachineReadable() {
        assertEquals("1500.50", Money.formatPlain(150_050L))
        assertEquals("0.01", Money.formatPlain(1L))
        assertEquals("1000000.00", Money.formatPlain(100_000_000L))
    }

    @Test
    fun formatKeepsSignAndCurrency() {
        val negative = Money.formatSigned(-50_000L)
        val positive = Money.formatSigned(50_000L)
        assertEquals(true, negative.startsWith("−"))
        assertEquals(true, positive.startsWith("+"))
        assertEquals(true, positive.endsWith("₽"))
    }

    @Test
    fun centArithmeticDoesNotLoseMoney() {
        // 0,1 ₽ + 0,2 ₽ = 0,3 ₽ ровно — с копейками это всегда так
        val sum = Money.parseToCents("0,10")!! + Money.parseToCents("0,20")!!
        assertEquals(Money.parseToCents("0,30"), sum)

        // А вот так «сломался» бы Double-подход:
        val doubleSum = 0.1 + 0.2
        assertEquals(false, doubleSum == 0.3)
    }
}
