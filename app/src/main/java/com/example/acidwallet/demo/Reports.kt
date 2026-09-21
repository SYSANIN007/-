package com.example.acidwallet.demo

/**
 * Отчёты ACID-лаборатории.
 *
 * Эти модели — «протокол эксперимента»: что подали на вход, что получили
 * на выходе, сошёлся ли инвариант. Именно они показываются в UI.
 */

/** Один шаг демонстрации атомарности. */
data class AtomicityStep(
    val title: String,
    val description: String,
    /** Сколько строк журнала код пытался записать. */
    val attemptedRows: Int,
    /** Сколько строк журнала реально осталось в базе после шага. */
    val committedRows: Int,
    /** На сколько код собирался изменить баланс. */
    val attemptedBalanceDeltaCents: Long,
    /** На сколько баланс изменился фактически. */
    val actualBalanceDeltaCents: Long,
    /** Расхождение «баланс ↔ журнал» после шага (0 — инвариант цел). */
    val invariantMismatchCents: Long,
    val errorMessage: String? = null,
    val code: String
) {
    val succeeded: Boolean get() = errorMessage == null

    /**
     * Свойство A (Atomicity): либо применилось ВСЁ (и баланс, и журнал),
     * либо не применилось НИЧЕГО.
     */
    val isAtomic: Boolean
        get() = if (succeeded) {
            committedRows == attemptedRows && actualBalanceDeltaCents == attemptedBalanceDeltaCents
        } else {
            committedRows == 0 && actualBalanceDeltaCents == 0L
        }

    /** Свойство C: расхождение баланса и журнала отсутствует. */
    val invariantHolds: Boolean get() = invariantMismatchCents == 0L
}

/** Полный отчёт демонстрации атомарности (шаги идут по порядку). */
data class AtomicityReport(
    val accountName: String,
    val accountId: Long,
    val startBalanceCents: Long,
    val journalRowsBefore: Int,
    val journalRowsAfter: Int,
    /** Шаг 1: корректный код внутри транзакции — коммит целиком. */
    val commitStep: AtomicityStep,
    /** Шаг 2: тот же код без транзакции — «полуприменённое» состояние. */
    val dangerousStep: AtomicityStep,
    /** Шаг 3: сбой внутри транзакции — полный откат. */
    val rollbackStep: AtomicityStep
) {
    /** Итог: база вернулась в согласованное состояние. */
    val consistent: Boolean
        get() = commitStep.invariantHolds && rollbackStep.invariantHolds &&
            rollbackStep.isAtomic && commitStep.isAtomic
}

/** Один сценарий лаборатории изоляции. */
data class IsolationCase(
    val index: Int,
    val title: String,
    val mode: String,
    val isolationApplied: Boolean,
    val expectedDeltaCents: Long,
    val observedDeltaCents: Long,
    val transactions: Int,
    val conflicts: Int,
    val totalReads: Int = 0,
    val inconsistentReads: Int = 0,
    val durationMs: Long,
    val conclusion: String,
    val code: String
) {
    /** Сошёлся ли ожидаемый итог с фактическим. */
    val ok: Boolean get() = expectedDeltaCents == observedDeltaCents
}

/** Итоговый протокол лаборатории изоляции. */
data class IsolationReport(
    val accountAName: String,
    val accountBName: String,
    val startBalanceACents: Long,
    val startBalanceBCents: Long,
    val cases: List<IsolationCase>,
    /** Сколько «лишних» денег появилось или исчезло за все эксперименты. */
    val totalDriftCents: Long
) {
    val startSumCents: Long get() = startBalanceACents + startBalanceBCents
    val invariantRestored: Boolean get() = totalDriftCents == 0L
}
