package com.example.acidwallet.demo

/**
 * Результаты живых демонстраций на настоящих пользовательских счетах.
 * Это те же операции, что делает кнопка в кошельке, — просто разложенные
 * на «правильный» и «неправильный» варианты кода.
 */

/** Итог «разорванного» перевода: одна бизнес-операция разбита на две транзакции. */
data class BrokenTransferReport(
    val fromName: String,
    val toName: String,
    val amountCents: Long,
    val failed: Boolean,
    val errorMessage: String?,
    val fromBeforeCents: Long,
    val fromAfterCents: Long,
    val toBeforeCents: Long,
    val toAfterCents: Long,
    val totalBeforeCents: Long,
    val totalAfterCents: Long
) {
    /** Насколько «уехала» сумма по всем счетам — при корректной работе это всегда 0. */
    val totalDriftCents: Long get() = totalAfterCents - totalBeforeCents

    val debitApplied: Boolean get() = fromAfterCents != fromBeforeCents
    val creditApplied: Boolean get() = toAfterCents != toBeforeCents
}

/** Итог стресс-теста: много параллельных транзакций одновременно. */
data class ParallelTransferReport(
    val requested: Int,
    val succeeded: Int,
    val failed: Int,
    val amountCents: Long,
    val fromName: String,
    val toName: String,
    val fromBeforeCents: Long,
    val fromAfterCents: Long,
    val toBeforeCents: Long,
    val toAfterCents: Long,
    val totalBeforeCents: Long,
    val totalAfterCents: Long,
    val journalRowsAdded: Int,
    val expectedJournalRows: Int,
    val durationMs: Long
) {
    val totalDriftCents: Long get() = totalAfterCents - totalBeforeCents

    /** Инвариант «сумма по всем счетам не изменилась». */
    val invariantOk: Boolean get() = totalDriftCents == 0L

    /** Ни одна половинка перевода не потерялась в журнале. */
    val journalIntact: Boolean get() = journalRowsAdded == expectedJournalRows

    /** Сколько ушло с первого счёта (знак зависит от направления переводов). */
    val transferredCents: Long get() = fromBeforeCents - fromAfterCents

    /** Балансы двух счетов сдвинулись строго зеркально: деньги не «испарились». */
    val balancesConserved: Boolean get() = transferredCents == (toAfterCents - toBeforeCents)
}
