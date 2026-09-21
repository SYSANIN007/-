package com.example.acidwallet.demo

import androidx.room.withTransaction
import com.example.acidwallet.data.DemoSeeder
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.domain.SimulatedGatewayFailure
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Демонстрация свойств A (Atomicity) и D (Durability) на настоящей SQLite.
 *
 * Эксперимент состоит из трёх шагов и выполняется на служебном счёте
 * «🧪 Демо-счёт транзакций» (в списке кошельков он скрыт, чтобы не мешать).
 *
 *   Шаг 1 — корректный код внутри `db.withTransaction { }`  → COMMIT целиком.
 *   Шаг 2 — тот же код БЕЗ транзакции + сбой в середине       → «полуприменённое»
 *           состояние: баланс изменился, а записи в журнале нет. Инвариант нарушен.
 *   Шаг 3 — сбой внутри `db.withTransaction { }`              → ROLLBACK: ни баланс,
 *           ни журнал не изменились.
 *
 * Все измерения настоящие: мы читаем строки из SQLite до и после каждого шага.
 */
class AtomicityLab(private val db: AppDatabase) {

    private val txn = db.transactionDemoDao()
    private val wallet = db.walletDao()

    /** Стартовый остаток лабораторного счёта — 1 000 ₽. */
    private val labStartCents = 100_000L

    private companion object {
        /** Комментарий-маркер для проверки долговечности. */
        const val DURABILITY_MARK = "Проверка долговечности"
    }

    /** Возвращает id служебного счёта транзакций, создавая его при необходимости. */
    suspend fun demoAccountId(): Long {
        txn.findAccountByName(DemoSeeder.DEMO_TX_NAME)?.let { return it.id }
        return db.withTransaction {
            txn.findAccountByName(DemoSeeder.DEMO_TX_NAME)?.id ?: txn.insertAccount(
                AccountEntity(
                    name = DemoSeeder.DEMO_TX_NAME,
                    ownerName = "ACID-лаборатория",
                    type = AccountType.CRYPTO,
                    balanceCents = labStartCents,
                    initialBalanceCents = labStartCents,
                    note = "Служебный счёт для демонстрации транзакций",
                    isDemo = true,
                    sortOrder = 92
                )
            )
        }
    }

    /**
     * Запускает полный эксперимент и возвращает протокол.
     * Лабораторный счёт в конце восстанавливается, чтобы опыт можно было повторить.
     */
    suspend fun run(onStage: suspend (String) -> Unit): AtomicityReport {
        val accountId = demoAccountId()

        // Чистим журнал и обнуляем баланс лабораторного счёта перед опытом.
        db.withTransaction {
            val now = System.currentTimeMillis()
            txn.deleteOperationsForAccount(accountId)
            txn.resetAccount(accountId, labStartCents, labStartCents, now)
        }

        val rowsBefore = txn.countOperations(accountId)
        val startBalance = txn.findBalance(accountId) ?: 0L

        onStage("Шаг 1/3 · изменения внутри транзакции")
        val commitStep = stepCommit(accountId)

        onStage("Шаг 2/3 · тот же код без транзакции")
        val dangerousStep = stepWithoutTransaction(accountId)

        onStage("Шаг 3/3 · сбой внутри транзакции")
        val rollbackStep = stepRollbackOnFailure(accountId)

        val rowsAfter = txn.countOperations(accountId)

        // Поломка из шага 2 «ремонтируется» вручную (так и приходится делать без
        // транзакций). Лабораторный счёт снова становится чистым.
        db.withTransaction {
            val now = System.currentTimeMillis()
            txn.deleteOperationsForAccount(accountId)
            txn.resetAccount(accountId, labStartCents, labStartCents, now)
        }

        return AtomicityReport(
            accountName = DemoSeeder.DEMO_TX_NAME,
            accountId = accountId,
            startBalanceCents = startBalance,
            journalRowsBefore = rowsBefore,
            journalRowsAfter = rowsAfter,
            commitStep = commitStep,
            dangerousStep = dangerousStep,
            rollbackStep = rollbackStep
        )
    }

    // ──────────────────────────── Шаг 1: COMMIT ────────────────────────────

    private suspend fun stepCommit(accountId: Long): AtomicityStep {
        val rowsBefore = txn.countOperations(accountId)
        val balanceBefore = txn.findBalance(accountId) ?: 0L
        val mismatchBefore = invariantMismatch(accountId)
        val attemptedRows = 2
        val attemptedDelta = 100_000L   // +1 000 ₽
        var error: String? = null

        try {
            db.withTransaction {
                val now = System.currentTimeMillis()
                txn.insertOperation(operation(accountId, 50_000L, "Пополнение №1", now))
                txn.insertOperation(operation(accountId, 50_000L, "Пополнение №2", now + 1))
                txn.updateBalance(accountId, balanceBefore + attemptedDelta, now)
                // Здесь транзакция завершается без ошибок → SQLite выполняет COMMIT.
            }
        } catch (t: Throwable) {
            error = t.message
        }

        val rows = txn.countOperations(accountId) - rowsBefore
        val balanceAfter = txn.findBalance(accountId) ?: 0L
        return AtomicityStep(
            title = "Шаг 1 · корректная транзакция",
            description = "Две записи журнала и изменение баланса выполнились в одной транзакции. " +
                "SQLite зафиксировал (COMMIT) всё сразу — база снова согласована.",
            attemptedRows = attemptedRows,
            committedRows = rows,
            attemptedBalanceDeltaCents = attemptedDelta,
            actualBalanceDeltaCents = balanceAfter - balanceBefore,
            invariantMismatchCents = invariantMismatch(accountId) - mismatchBefore,
            errorMessage = error,
            code = """
                db.withTransaction {
                    dao.insertOperation(...)   // запись №1
                    dao.insertOperation(...)   // запись №2
                    dao.updateBalance(id, newBalance)
                }                              // ← COMMIT: применяется всё сразу
            """.trimIndent()
        )
    }

    // ─────────────────────── Шаг 2: БЕЗ транзакции ───────────────────────

    /**
     * Классическая ошибка: изменение баланса и запись в журнал выполняются
     * двумя отдельными (автокоммитными) запросами. Между ними — сбой.
     * Списание уже в базе, а записи в журнале нет: деньги «испарились».
     */
    private suspend fun stepWithoutTransaction(accountId: Long): AtomicityStep {
        val rowsBefore = txn.countOperations(accountId)
        val balanceBefore = txn.findBalance(accountId) ?: 0L
        val mismatchBefore = invariantMismatch(accountId)
        val attemptedDelta = -50_000L   // −500 ₽
        var error: String? = null

        try {
            // Запрос №1. Автокоммит: списание сразу становится видимым всем читателям.
            txn.updateBalance(accountId, balanceBefore + attemptedDelta, System.currentTimeMillis())

            // ...в этот момент приложение падает (или процесс убит системой).
            // Запись в журнал — запрос №2 — уже не выполняется.
            throw SimulatedGatewayFailure("процесс приложения остановлен после списания")
        } catch (t: SimulatedGatewayFailure) {
            error = t.reason
        }

        val rows = txn.countOperations(accountId) - rowsBefore
        val balanceAfter = txn.findBalance(accountId) ?: 0L
        return AtomicityStep(
            title = "Шаг 2 · тот же код без транзакции",
            description = "Баланс уменьшился, а операции в журнале нет. Инвариант " +
                "«баланс = начальный остаток + Σ журнала» сломан: деньги списались «в никуда». " +
                "Так выглядит отсутствие свойства A (Atomicity).",
            attemptedRows = 1,
            committedRows = rows,
            attemptedBalanceDeltaCents = attemptedDelta,
            actualBalanceDeltaCents = balanceAfter - balanceBefore,
            invariantMismatchCents = invariantMismatch(accountId) - mismatchBefore,
            errorMessage = error,
            code = """
                dao.updateBalance(id, balance - 50_000)  // ← автокоммит: УЖЕ в базе
                throw GatewayFailed()                    // ← сбой сразу после этого
                dao.insertOperation(...)                 // ← сюда код не дошёл
            """.trimIndent()
        )
    }

    // ─────────────────────── Шаг 3: ROLLBACK ───────────────────────

    /** Тот же сбой, но теперь внутри транзакции: SQLite откатывает абсолютно всё. */
    private suspend fun stepRollbackOnFailure(accountId: Long): AtomicityStep {
        val rowsBefore = txn.countOperations(accountId)
        val balanceBefore = txn.findBalance(accountId) ?: 0L
        val mismatchBefore = invariantMismatch(accountId)
        val attemptedDelta = 90_000L    // +900 ₽
        var error: String? = null

        try {
            db.withTransaction {
                val now = System.currentTimeMillis()
                txn.insertOperation(operation(accountId, 30_000L, "Платёж №1", now))
                txn.insertOperation(operation(accountId, 30_000L, "Платёж №2", now + 1))
                txn.insertOperation(operation(accountId, 30_000L, "Платёж №3", now + 2))
                txn.updateBalance(accountId, balanceBefore + attemptedDelta, now)

                // Внешний шлюз не подтвердил платёж — исключение ДО конца транзакции.
                throw SimulatedGatewayFailure("шлюз не подтвердил перевод")
            }
        } catch (t: SimulatedGatewayFailure) {
            error = t.reason
        }

        val rows = txn.countOperations(accountId) - rowsBefore
        val balanceAfter = txn.findBalance(accountId) ?: 0L
        return AtomicityStep(
            title = "Шаг 3 · сбой внутри транзакции → ROLLBACK",
            description = "Произошло исключение, и Room/SQLite откатил транзакцию целиком: " +
                "ни одной записи журнала, баланс не изменился. Приложение должно лишь " +
                "показать пользователю ошибку — «починить» данные вручную не нужно.",
            attemptedRows = 3,
            committedRows = rows,
            attemptedBalanceDeltaCents = attemptedDelta,
            actualBalanceDeltaCents = balanceAfter - balanceBefore,
            invariantMismatchCents = invariantMismatch(accountId) - mismatchBefore,
            errorMessage = error,
            code = """
                try {
                    db.withTransaction {
                        repeat(3) { dao.insertOperation(...) }
                        dao.updateBalance(id, balance + 90_000)
                        throw GatewayFailed()       // ← сбой до конца транзакции
                    }
                } catch (e: GatewayFailed) {
                    // ROLLBACK уже выполнен SQLite:
                    // в базе НЕТ ни строк журнала, ни нового баланса
                }
            """.trimIndent()
        )
    }

    // ─────────────────────────── Вспомогательное ───────────────────────────

    /**
     * Расхождение инварианта: `баланс − (начальный остаток + Σ журнала)`.
     * Ноль означает, что CONSISTENCY в порядке.
     */
    private suspend fun invariantMismatch(accountId: Long): Long {
        val account = txn.findAccount(accountId) ?: return 0L
        val journalSum = wallet.sumActiveOperations(accountId)
        return account.balanceCents - (account.initialBalanceCents + journalSum)
    }

    private fun operation(accountId: Long, amountCents: Long, comment: String, now: Long) = OperationEntity(
        accountId = accountId,
        type = if (amountCents >= 0) OperationType.DEPOSIT else OperationType.WITHDRAWAL,
        amountCents = amountCents,
        status = OperationStatus.COMPLETED,
        comment = comment,
        createdAt = now,
        updatedAt = now
    )

    // ──────────────────────────── D: Durability ────────────────────────────

    /**
     * Оставляет «отметку долговечности» в журнале.
     *
     * Проверка: нажмите кнопку, полностью закройте приложение (свайпом из списка
     * недавних задач) и откройте снова — количество отметок сохранится.
     * Это и есть свойство D: зафиксированные данные переживают завершение процесса.
     */
    suspend fun leaveDurabilityMark(): Int {
        val accountId = txn.findAccountByName(DemoSeeder.DURABILITY_NAME)?.id
            ?: createDurabilityAccount()
        val now = System.currentTimeMillis()
        db.withTransaction {
            txn.insertOperation(
                OperationEntity(
                    accountId = accountId,
                    type = OperationType.SYSTEM,
                    amountCents = 0L,
                    status = OperationStatus.COMPLETED,
                    comment = "$DURABILITY_MARK · ${timestamp(now)}",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        return txn.countOperations(accountId)
    }

    /** Текущее количество отметок долговечности в базе. */
    suspend fun durabilityMarks(): Int = txn.findAccountByName(DemoSeeder.DURABILITY_NAME)
        ?.let { txn.countOperations(it.id) } ?: 0

    /** id счёта-журнала проверок (для реакции UI через Flow). */
    suspend fun durabilityAccountId(): Long = txn.findAccountByName(DemoSeeder.DURABILITY_NAME)?.id
        ?: createDurabilityAccount()

    private suspend fun createDurabilityAccount(): Long = db.withTransaction {
        txn.insertAccount(
            AccountEntity(
                name = DemoSeeder.DURABILITY_NAME,
                ownerName = "ACID-лаборатория",
                type = AccountType.SAVINGS,
                balanceCents = 0L,
                initialBalanceCents = 0L,
                note = "Служебный счёт: отметки переживают перезапуск приложения",
                isDemo = true,
                sortOrder = 93
            )
        )
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("ru", "RU")).format(Date(millis))
}
