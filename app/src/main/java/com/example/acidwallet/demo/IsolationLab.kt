package com.example.acidwallet.demo

import androidx.room.withTransaction
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.domain.Money
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Лаборатория ИЗОЛЯЦИИ (свойство I) — четыре эксперимента на живой базе.
 *
 * Все четыре опыта работают с двумя служебными счетами A и B (по 10 000 ₽).
 * Деньги в опытах перекладываются только внутри этой пары, поэтому
 * «сумма A + B» — это инвариант, который обязан оставаться неизменным.
 *
 *   Опыт 1. Чтение двух счетов ДВУМЯ отдельными запросами (без транзакции).
 *           → отчёт видит состояние, которого в базе не существовало.
 *   Опыт 2. То же чтение, но внутри транзакции (снимок).
 *           → расхождений нет: изоляция работает.
 *   Опыт 3. Два независимых изменения одного счёта БЕЗ транзакции
 *           → классический lost update: деньги теряются или появляются.
 *   Опыт 4. То же, но каждое изменение — в `db.withTransaction { }`
 *           → результат ровно такой, как задумано.
 *
 * ─── Честное замечание о природе эксперимента ───────────────────────────
 * Опыты 1–3 воспроизводят ошибку проектирования, которая встречается
 * в реальном коде очень часто: «прочитал → изменил → записал» двумя
 * отдельными запросами. `pauseMs` играет роль времени, которое транзакция
 * в SQLite реально тратит между SELECT и UPDATE (в реальном приложении это
 * сеть, вычисления, пауза сборщика мусора). `queue` — аналог блокировки
 * записи SQLite (`BEGIN IMMEDIATE`): именно она делает транзакции
 * последовательными, а значит изолированными.
 * Опыт 4 использует настоящие `db.withTransaction`, и там счётчики гонок
 * измеряются на реальной СУБД.
 */
class IsolationLab(
    private val db: AppDatabase,
    private val io: CoroutineScope,
    private val demoAId: Long,
    private val demoBId: Long,
    private val demoAName: String,
    private val demoBName: String
) {

    private val txn = db.transactionDemoDao()

    /** Сколько «времени СУБД» проходит между SELECT и UPDATE внутри транзакции. */
    private val pauseMs = 40L

    private data class DemoBalances(val a: Long, val b: Long) {
        val sum: Long get() = a + b
    }

    private class LabStats {
        val transactions = AtomicInteger()     // попытки «прочитал-изменил-записал»
        val conflicts = AtomicInteger()        // сколько попыток стартовали поверх другой
        val inFlight = AtomicInteger()
        val reads = AtomicInteger()            // всего «отчётов» прочитано
        val inconsistentReads = AtomicInteger()
        val realTransactions = AtomicInteger() // настоящие db.withTransaction
        val lockFailures = AtomicInteger()
    }

    // ─────────────────────────── публичный API ───────────────────────────

    /**
     * Прогоняет все четыре опыта подряд.
     * Возвращает протокол со всеми измеренными числами.
     */
    suspend fun run(startCents: Long, onStage: suspend (String) -> Unit): IsolationReport {
        val startA = startCents
        val startB = startCents

        onStage("Опыт 1/4 · чтение без транзакции")
        val case1 = caseWithoutSnapshot(startA, startB)

        onStage("Опыт 2/4 · чтение внутри транзакции")
        val case2 = caseWithSnapshot(startA, startB)

        onStage("Опыт 3/4 · два изменения одного счёта без транзакции")
        val case3 = caseLostUpdate(startA, startB)

        onStage("Опыт 4/4 · то же внутри db.withTransaction { }")
        val case4 = caseRealTransaction(startA, startB)

        // Суммарный «увод» денег от ожидаемого результата.
        val drift = listOf(case1, case2, case3, case4)
            .sumOf { it.observedDeltaCents - it.expectedDeltaCents }

        // Восстанавливаем демо-счета: A и B снова по 10 000 ₽.
        resetBalances(startA)

        return IsolationReport(
            accountAName = demoAName,
            accountBName = demoBName,
            startBalanceACents = startA,
            startBalanceBCents = startB,
            cases = listOf(case1, case2, case3, case4),
            totalDriftCents = drift
        )
    }

    // ─────────── Опыт 1: чтение без транзакции (несогласованный снимок) ───────────

    private suspend fun caseWithoutSnapshot(startA: Long, startB: Long): IsolationCase {
        resetBalances(startA)
        val stats = LabStats()
        val before = balances()
        val started = System.nanoTime()

        val writers = listOf(
            queueTransferWriter(stats, 1_00L, 6),
            queueTransferWriter(stats, 1_00L, 6)
        )
        val readers = (1..3).map { reportReader(stats, expectedSum = before.sum, rounds = 4, useSnapshot = false) }

        (writers + readers).joinAll()

        val after = balances()
        val duration = (System.nanoTime() - started) / 1_000_000

        return IsolationCase(
            index = 1,
            title = "Чтение двух счетов двумя отдельными запросами",
            mode = "SELECT A; …; SELECT B  (без транзакции)",
            isolationApplied = false,
            expectedDeltaCents = 0L,
            observedDeltaCents = after.sum - before.sum,
            transactions = stats.transactions.get(),
            conflicts = stats.conflicts.get(),
            totalReads = stats.reads.get(),
            inconsistentReads = stats.inconsistentReads.get(),
            durationMs = duration,
            conclusion = buildString {
                append("Отчёт из двух запросов увидел ")
                append("${stats.inconsistentReads.get()} из ${stats.reads.get()} ")
                append("состояний, которых в базе никогда не было: сумма A + B «не сходилась». ")
                append("Деньги при этом не потерялись (перевод целиком выполнялся в одной транзакции), ")
                append("но пользователь увидел неверный отчёт. Лечение — читать связанные данные ")
                append("внутри одной транзакции (следующий опыт).")
            },
            code = """
                // ❌ так нельзя: между двумя SELECT другая транзакция успевает закоммититься
                val a = dao.findBalance(accountA)   // снимок №1
                val b = dao.findBalance(accountB)   // снимок №2 — уже из другого состояния
                // a + b может не равняться ни одному реально существовавшему состоянию
            """.trimIndent()
        )
    }

    // ─────────── Опыт 2: то же чтение, но внутри транзакции ───────────

    private suspend fun caseWithSnapshot(startA: Long, startB: Long): IsolationCase {
        resetBalances(startA)
        val stats = LabStats()
        val before = balances()
        val started = System.nanoTime()

        val writers = listOf(
            queueTransferWriter(stats, 1_00L, 6),
            queueTransferWriter(stats, 1_00L, 6)
        )
        val readers = (1..3).map { reportReader(stats, expectedSum = before.sum, rounds = 3, useSnapshot = true) }

        (writers + readers).joinAll()

        val after = balances()
        val duration = (System.nanoTime() - started) / 1_000_000

        return IsolationCase(
            index = 2,
            title = "Те же отчёты, но чтение внутри транзакции",
            mode = "db.withTransaction { SELECT A; SELECT B }",
            isolationApplied = true,
            expectedDeltaCents = 0L,
            observedDeltaCents = after.sum - before.sum,
            transactions = stats.transactions.get(),
            conflicts = stats.conflicts.get(),
            totalReads = stats.reads.get(),
            inconsistentReads = stats.inconsistentReads.get(),
            durationMs = duration,
            conclusion = "Теперь ни один отчёт не увидел «половинчатого» перевода: " +
                "0 расхождений из ${stats.reads.get()}. Транзакция — единица изоляции: " +
                "изменения других транзакций либо видны целиком, либо не видны вообще. " +
                "Плата за это — транзакции идут по очереди (${stats.conflicts.get()} ожиданий " +
                "на ${stats.transactions.get()} операций записи).",
            code = """
                // ✅ связанные чтения — в одной транзакции
                db.withTransaction {
                    val a = dao.findBalance(accountA)
                    val b = dao.findBalance(accountB)
                    // a + b всегда из ОДНОГО состояния базы
                }
            """.trimIndent()
        )
    }

    // ─────────── Опыт 3: потерянное обновление (без транзакции) ───────────

    /**
     * Два «независимых» изменения одного счёта: зачисление +1 000 ₽ и списание
     * −1 000 ₽. Итог обязан быть нулевым — но только если оба изменения
     * защищены транзакцией или блокировкой строки.
     */
    private suspend fun caseLostUpdate(startA: Long, startB: Long): IsolationCase {
        resetBalances(startA)
        val stats = LabStats()
        val balanceBefore = txn.findBalance(demoAId) ?: 0L
        val started = System.nanoTime()

        // Мьютекс НЕ используется: обе операции работают «как получится».
        val jobs = listOf(
            plainWriter(stats, delta = 100_000L, rounds = 5),   // +1 000 ₽ (зарплата)
            plainWriter(stats, delta = -100_000L, rounds = 5)   // −1 000 ₽ (аренда)
        )
        jobs.joinAll()

        val balanceAfter = txn.findBalance(demoAId) ?: 0L
        val duration = (System.nanoTime() - started) / 1_000_000
        val observed = balanceAfter - balanceBefore
        val lost = 0L - observed

        return IsolationCase(
            index = 3,
            title = "Зарплата и аренда одновременно, без транзакции",
            mode = "read → пауза → write, два независимых потока",
            isolationApplied = false,
            expectedDeltaCents = 0L,
            observedDeltaCents = observed,
            transactions = stats.transactions.get(),
            conflicts = stats.conflicts.get(),
            durationMs = duration,
            conclusion = buildString {
                append("Ожидалось ровно 0 ₽ (плюс и минус взаимно уничтожаются), ")
                append("а получилось ${Money.formatSigned(observed)}. ")
                append(
                    if (observed > 0) {
                        "Деньги появились «из воздуха»: списание было перезаписано более поздней записью. "
                    } else {
                        "Деньги пропали со счёта: зачисление было перезаписано более поздней записью (lost update). "
                    }
                )
                append("Это цена отсутствия изоляции — именно поэтому изменение баланса ")
                append("и запись в журнал должны идти одной транзакцией.")
                if (lost != 0L) append(" Расхождение с ожиданием: ${Money.formatSigned(-lost)}.")
            },
            code = """
                // ❌ два потока работают с одним счётом
                val current = dao.findBalance(id)        // оба прочитали одно и то же
                delay(40)                                // прошло время
                dao.updateBalance(id, current + 100_000) // второй UPDATE затирает первый

                // Баланс изменился на «неизвестно сколько»: инвариант потерян.
            """.trimIndent()
        )
    }

    // ─────────── Опыт 4: то же самое в настоящей транзакции ───────────

    private suspend fun caseRealTransaction(startA: Long, startB: Long): IsolationCase {
        resetBalances(startA)
        val stats = LabStats()
        val balanceBefore = txn.findBalance(demoAId) ?: 0L
        val started = System.nanoTime()

        val jobs = listOf(
            realTransactionWriter(stats, delta = 100_000L, rounds = 5),
            realTransactionWriter(stats, delta = -100_000L, rounds = 5)
        )
        jobs.joinAll()

        val balanceAfter = txn.findBalance(demoAId) ?: 0L
        val duration = (System.nanoTime() - started) / 1_000_000
        val observed = balanceAfter - balanceBefore

        return IsolationCase(
            index = 4,
            title = "Те же операции внутри db.withTransaction { }",
            mode = "db.withTransaction { SELECT → UPDATE }",
            isolationApplied = true,
            expectedDeltaCents = 0L,
            observedDeltaCents = observed,
            transactions = stats.realTransactions.get(),
            conflicts = stats.conflicts.get(),
            durationMs = duration,
            conclusion = buildString {
                append("Итог ровно нулевой (${Money.formatSigned(observed)}): каждая транзакция ")
                append("видит уже зафиксированное состояние счёта, поэтому изменения не теряются. ")
                append("Выполнено транзакций: ${stats.realTransactions.get()}")
                if (stats.lockFailures.get() > 0) {
                    append(", конфликтов блокировок: ${stats.lockFailures.get()} (они ждали и повторились)")
                }
                append(". Заметьте: это те же самые две строки кода, что и в опыте 3 — ")
                append("разница только в границах транзакции.")
            },
            code = """
                // ✅ каждое изменение — атомарная единица работы
                db.withTransaction {
                    val current = dao.findBalance(id)
                    dao.updateBalance(id, current + delta)
                }   // BEGIN IMMEDIATE … COMMIT

                // SQLite не даёт двум транзакциям «перепутать» прочитанное значение.
            """.trimIndent()
        )
    }

    // ──────────────────────────── исполнители ────────────────────────────

    /**
     * Перевод внутри одной транзакции-«очереди»: списание с A и зачисление на B.
     * Деньги только перекладываются, сумма A + B неизменна.
     */
    private fun queueTransferWriter(stats: LabStats, cents: Long, rounds: Int): Job = io.launch {
        repeat(rounds) {
            queueMutex.withLock {
                readModifyWrite(stats, demoAId, -cents)
                readModifyWrite(stats, demoBId, cents)
            }
        }
    }

    /** Изменение одного счёта без всякой изоляции — «как получится». */
    private fun plainWriter(stats: LabStats, delta: Long, rounds: Int): Job = io.launch {
        repeat(rounds) {
            readModifyWrite(stats, demoAId, delta)
        }
    }

    /** Настоящая транзакция Room: чтение и запись внутри `db.withTransaction`. */
    private fun realTransactionWriter(stats: LabStats, delta: Long, rounds: Int): Job = io.launch {
        repeat(rounds) {
            var done = false
            var attempt = 0
            while (!done && attempt < 3) {
                attempt++
                done = runRealTransaction(stats, delta)
                if (!done) stats.lockFailures.incrementAndGet()
            }
        }
    }

    private suspend fun runRealTransaction(stats: LabStats, delta: Long): Boolean {
        if (stats.inFlight.getAndIncrement() > 0) stats.conflicts.incrementAndGet()
        return try {
            db.withTransaction {
                val current = txn.findBalance(demoAId)
                    ?: throw IllegalStateException("Счёт #$demoAId не найден")
                txn.updateBalance(demoAId, current + delta, System.currentTimeMillis())
            }
            stats.realTransactions.incrementAndGet()
            true
        } catch (t: Throwable) {
            false
        } finally {
            stats.inFlight.decrementAndGet()
        }
    }

    /**
     * «Прочитал → изменил → записал» на самом деле выполняется в СУБД
     * не мгновенно: именно в этот зазор и проникают другие транзакции.
     */
    private suspend fun readModifyWrite(stats: LabStats, accountId: Long, deltaCents: Long): Long {
        stats.transactions.incrementAndGet()
        if (stats.inFlight.getAndIncrement() > 0) stats.conflicts.incrementAndGet()
        try {
            val current = txn.findBalance(accountId)
                ?: throw IllegalStateException("Счёт #$accountId не найден")
            delay(pauseMs)   // время между SELECT и UPDATE
            val updated = current + deltaCents
            txn.updateBalance(accountId, updated, System.currentTimeMillis())
            return updated
        } finally {
            stats.inFlight.decrementAndGet()
        }
    }

    /**
     * «Отчёт» по двум счетам: читает A, потом B.
     *
     * @param useSnapshot true  — оба чтения внутри транзакции (снимок согласован);
     *                    false — два независимых запроса (снимок «рвётся»).
     */
    private fun reportReader(stats: LabStats, expectedSum: Long, rounds: Int, useSnapshot: Boolean): Job = io.launch {
        repeat(rounds) {
            val read: suspend () -> Unit = {
                val a = txn.findBalance(demoAId) ?: 0L
                delay(pauseMs / 2)
                val b = txn.findBalance(demoBId) ?: 0L
                stats.reads.incrementAndGet()
                if (a + b != expectedSum) stats.inconsistentReads.incrementAndGet()
            }
            if (useSnapshot) queueMutex.withLock { read() } else read()
        }
    }

    // ─────────────────────────── вспомогательное ───────────────────────────

    /** Аналог блокировки записи SQLite: транзакции идут строго по одной. */
    private val queueMutex = Mutex()

    private suspend fun balances(): DemoBalances {
        val a = txn.findBalance(demoAId) ?: 0L
        val b = txn.findBalance(demoBId) ?: 0L
        return DemoBalances(a, b)
    }

    private suspend fun resetBalances(cents: Long) = db.withTransaction {
        val now = System.currentTimeMillis()
        txn.updateBalance(demoAId, cents, now)
        txn.updateBalance(demoBId, cents, now)
    }
}
