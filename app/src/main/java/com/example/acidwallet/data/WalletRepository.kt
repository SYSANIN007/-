package com.example.acidwallet.data

import androidx.room.withTransaction
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AccountWithOperations
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.local.OperationWithAccount
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.demo.BrokenTransferReport
import com.example.acidwallet.demo.ParallelTransferReport
import com.example.acidwallet.domain.Money
import com.example.acidwallet.domain.SimulatedGatewayFailure
import com.example.acidwallet.domain.WalletException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Репозиторий кошелька: вся бизнес-логика приложения.
 *
 * ┌─────────────── CRUD ────────────────┐   ┌────────────────── ACID ──────────────────┐
 * │ create: insertAccount/insertOperation│   │ A — db.withTransaction: либо всё, либо    │
 * │ read:   observeXxx (Flow) / findXxx  │   │     ничего (ROLLBACK при исключении)      │
 * │ update: updateAccount/patchOperation │   │ C — проверки инвариантов ВНУТРИ          │
 * │         + пересчёт баланса          │   │     транзакции: баланс ≥ 0, счёт активен  │
 * │ delete: deleteAccount (CASCADE)      │   │ I — транзакции выполняются по одной       │
 * │         deleteOperation (+ откат)    │   │     (SQLite write lock, режим WAL)        │
 * └─────────────────────────────────────┘   │ D — COMMIT в WAL-файл: данные переживут   │
 *                                           │     закрытие приложения                   │
 *                                           └───────────────────────────────────────────┘
 *
 * Ключевое правило, которое видно в коде: НИ ОДНА операция не меняет баланс
 * «в отрыве» от журнала. Изменение баланса и запись в журнал всегда находятся
 * в одной транзакции — именно это обеспечивает свойство A (атомарность)
 * и позволяет свойству C (согласованность) проверять инвариант
 * balance == initialBalance + Σ(журнал).
 */
class WalletRepository(private val db: AppDatabase) {

    private val wallet = db.walletDao()
    private val seeder = DemoSeeder(db)

    // ─────────────────────────────── READ ───────────────────────────────

    /** Реактивный список счетов с журналами — обновляется после любого CRUD. */
    fun observeAccounts(): Flow<List<AccountWithOperations>> = wallet.observeAccountsWithOperations()

    /** «Итого» по пользовательским счетам. */
    fun observeTotalBalance(): Flow<Long> = wallet.observeTotalBalanceCents()

    /** Общая лента последних операций по всем счетам. */
    fun observeActivity(limit: Int = 30): Flow<List<OperationWithAccount>> =
        db.isolationDao().observeRecentActivity(limit)

    /** Сумма по ВСЕМ счетам (включая лабораторные) — проверка инварианта. */
    suspend fun totalAllAccountsCents(): Long = wallet.totalBalanceAllAccountsCents()

    suspend fun findAccount(id: Long): AccountEntity? = wallet.findAccountById(id)

    /** Текущий режим журнала SQLite — показываем в карточке Durability. */
    suspend fun journalMode(): String = db.journalMode()

    // ────────────────────────────── CREATE ──────────────────────────────

    /**
     * Создание счёта. Внутри одной транзакции: строка счёта + системная запись
     * журнала «Начальный остаток». Если что-то из этого не выполнится — откатится всё.
     */
    suspend fun createAccount(
        name: String,
        ownerName: String,
        type: AccountType,
        initialBalanceCents: Long,
        note: String
    ): Long {
        if (name.isBlank()) throw WalletException.InvalidAmount()
        if (initialBalanceCents < 0) throw WalletException.InvalidAmount()

        return db.withTransaction {
            val now = System.currentTimeMillis()
            val accountId = wallet.insertAccount(
                AccountEntity(
                    name = name.trim(),
                    ownerName = ownerName.trim().ifBlank { "Владелец" },
                    type = type,
                    balanceCents = initialBalanceCents,
                    initialBalanceCents = initialBalanceCents,
                    note = note.trim(),
                    sortOrder = (wallet.getAllAccounts().maxOfOrNull { it.sortOrder } ?: 0) + 1,
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (initialBalanceCents > 0) {
                wallet.insertOperation(
                    OperationEntity(
                        accountId = accountId,
                        type = OperationType.SYSTEM,
                        amountCents = initialBalanceCents,
                        status = OperationStatus.COMPLETED,
                        comment = "Начальный остаток счёта",
                        localSeq = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            accountId
        }
    }

    // ────────────────────────────── UPDATE ──────────────────────────────

    /** Изменение «паспортных» данных счёта (имя, владелец, тип, заметка, архив). */
    suspend fun updateAccount(
        accountId: Long,
        name: String,
        ownerName: String,
        type: AccountType,
        note: String,
        isActive: Boolean
    ) = db.withTransaction {
        val account = wallet.findAccountById(accountId)
            ?: throw WalletException.AccountNotFound(accountId)
        if (name.isBlank()) throw WalletException.InvalidAmount()
        wallet.updateAccount(
            account.copy(
                name = name.trim(),
                ownerName = ownerName.trim().ifBlank { "Владелец" },
                type = type,
                note = note.trim(),
                isActive = isActive,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /** Пополнение счёта: баланс + журнал в одной транзакции. */
    suspend fun deposit(accountId: Long, amountCents: Long, comment: String) {
        if (amountCents <= 0) throw WalletException.InvalidAmount()
        db.withTransaction {
            val account = wallet.findAccountById(accountId)
                ?: throw WalletException.AccountNotFound(accountId)
            if (!account.isActive) throw WalletException.AccountArchived(account.name)
            val now = System.currentTimeMillis()
            wallet.updateBalance(account.id, account.balanceCents + amountCents, now)
            wallet.insertOperation(
                OperationEntity(
                    accountId = account.id,
                    type = OperationType.DEPOSIT,
                    amountCents = amountCents,
                    status = OperationStatus.COMPLETED,
                    comment = comment.ifBlank { "Пополнение" },
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Списание со счёта: проверка «хватает ли денег» и обе записи (баланс + журнал)
     * выполняются в одной транзакции — промежуточного состояния не существует.
     */
    suspend fun withdraw(accountId: Long, amountCents: Long, comment: String) {
        if (amountCents <= 0) throw WalletException.InvalidAmount()
        db.withTransaction {
            val account = wallet.findAccountById(accountId)
                ?: throw WalletException.AccountNotFound(accountId)
            if (!account.isActive) throw WalletException.AccountArchived(account.name)
            if (account.balanceCents < amountCents) {
                throw WalletException.InsufficientFunds(account.balanceCents, amountCents)
            }
            val now = System.currentTimeMillis()
            wallet.updateBalance(account.id, account.balanceCents - amountCents, now)
            wallet.insertOperation(
                OperationEntity(
                    accountId = account.id,
                    type = OperationType.WITHDRAWAL,
                    amountCents = -amountCents,
                    status = OperationStatus.COMPLETED,
                    comment = comment.ifBlank { "Списание" },
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * Перевод между счетами — эталонная атомарная операция:
     * списание, две записи журнала и зачисление выполняются как единое целое.
     * Любая ошибка (нет денег, счёт в архиве, падение процесса) → полный ROLLBACK,
     * и «половинчатого» перевода в базе не остаётся.
     */
    suspend fun transfer(fromAccountId: Long, toAccountId: Long, amountCents: Long, comment: String) {
        if (amountCents <= 0) throw WalletException.InvalidAmount()
        if (fromAccountId == toAccountId) throw WalletException.SameAccount()

        db.withTransaction {
            val from = wallet.findAccountById(fromAccountId)
                ?: throw WalletException.AccountNotFound(fromAccountId)
            val to = wallet.findAccountById(toAccountId)
                ?: throw WalletException.AccountNotFound(toAccountId)

            if (!from.isActive) throw WalletException.AccountArchived(from.name)
            if (!to.isActive) throw WalletException.AccountArchived(to.name)
            // CONSISTENCY: денег нельзя отправить больше, чем есть на счёте
            if (from.balanceCents < amountCents) {
                throw WalletException.InsufficientFunds(from.balanceCents, amountCents)
            }

            val now = System.currentTimeMillis()
            val groupId = UUID.randomUUID().toString()

            // Шаг 1. Списание
            wallet.updateBalance(from.id, from.balanceCents - amountCents, now)
            wallet.insertOperation(
                OperationEntity(
                    accountId = from.id,
                    type = OperationType.TRANSFER_OUT,
                    amountCents = -amountCents,
                    status = OperationStatus.COMPLETED,
                    comment = comment.ifBlank { "Перевод на «${to.name}»" },
                    groupId = groupId,
                    localSeq = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // Шаг 2. Зачисление
            wallet.updateBalance(to.id, to.balanceCents + amountCents, now)
            wallet.insertOperation(
                OperationEntity(
                    accountId = to.id,
                    type = OperationType.TRANSFER_IN,
                    amountCents = amountCents,
                    status = OperationStatus.COMPLETED,
                    comment = comment.ifBlank { "Перевод с «${from.name}»" },
                    groupId = groupId,
                    localSeq = 1,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    /**
     * UPDATE операции (сумма и комментарий). Баланс корректируется на дельту —
     * и снова в одной транзакции с изменением журнала.
     */
    suspend fun updateOperation(operationId: Long, newAmountCents: Long, comment: String) {
        db.withTransaction {
            val operation = wallet.findOperationById(operationId)
                ?: throw WalletException.AccountNotFound(operationId)
            if (operation.status == OperationStatus.CANCELLED) throw WalletException.AlreadyCancelled()
            if (operation.type == OperationType.SYSTEM) throw WalletException.ImmutableOperation()

            val account = wallet.findAccountById(operation.accountId)
                ?: throw WalletException.AccountNotFound(operation.accountId)

            val now = System.currentTimeMillis()
            val delta = newAmountCents - operation.amountCents
            if (delta != 0L) {
                val newBalance = account.balanceCents + delta
                if (newBalance < 0) {
                    throw WalletException.InsufficientFunds(account.balanceCents, -delta)
                }
                wallet.updateBalance(account.id, newBalance, now)
            }
            wallet.patchOperation(
                operationId = operationId,
                comment = comment.ifBlank { "Операция" },
                amountCents = newAmountCents,
                status = operation.status,
                updatedAt = now
            )
        }
    }

    /**
     * Отмена операции (это не DELETE, а UPDATE статуса — аудит-лог сохраняется).
     * Баланс возвращается к состоянию «как будто операции не было».
     */
    suspend fun cancelOperation(operationId: Long) {
        db.withTransaction {
            val operation = wallet.findOperationById(operationId)
                ?: throw WalletException.AccountNotFound(operationId)
            if (operation.status == OperationStatus.CANCELLED) throw WalletException.AlreadyCancelled()
            if (operation.type == OperationType.SYSTEM) throw WalletException.ImmutableOperation()

            val account = wallet.findAccountById(operation.accountId)
                ?: throw WalletException.AccountNotFound(operation.accountId)

            val now = System.currentTimeMillis()
            if (operation.status == OperationStatus.COMPLETED) {
                val newBalance = account.balanceCents - operation.amountCents
                if (newBalance < 0) {
                    throw WalletException.InsufficientFunds(account.balanceCents, -operation.amountCents)
                }
                wallet.updateBalance(account.id, newBalance, now)
            }
            wallet.patchOperation(
                operationId = operationId,
                comment = operation.comment,
                amountCents = operation.amountCents,
                status = OperationStatus.CANCELLED,
                updatedAt = now
            )
        }
    }

    /**
     * Сверка журнала: пересчитывает балансы всех пользовательских счетов
     * по инварианту `баланс == начальный остаток + Σ(проведённых операций)`.
     *
     * Все правки выполняются в ОДНОЙ транзакции — либо база останется
     * полностью согласованной, либо не изменится вообще.
     */
    suspend fun recalculateBalances(): RecalculationReport = db.withTransaction {
        val accounts = wallet.getAccountsWithOperations()
        val fixes = mutableListOf<String>()
        val now = System.currentTimeMillis()

        accounts.forEach { entry ->
            val activeSum = wallet.sumActiveOperations(entry.account.id)
            val expected = entry.account.initialBalanceCents + activeSum
            if (entry.account.balanceCents != expected) {
                fixes += "«${entry.account.name}»: ${Money.format(entry.account.balanceCents)} → ${Money.format(expected)}"
                wallet.updateBalance(entry.account.id, expected, now)
            }
        }
        RecalculationReport(checkedAccounts = accounts.size, fixedAccounts = fixes)
    }

    // ────────────────────────────── DELETE ──────────────────────────────

    /**
     * Удаление счёта вместе с журналом (ON DELETE CASCADE).
     * Если внешний ключ почему-то выключен, операция обязана упасть — мы не хотим
     * «осиротевших» строк в operations (это нарушило бы CONSISTENCY).
     */
    suspend fun deleteAccount(accountId: Long) = db.withTransaction {
        val account = wallet.findVisibleAccount(accountId)
            ?: throw WalletException.AccountNotFound(accountId)
        wallet.deleteAccount(account)
    }

    /**
     * Удаление отдельной операции с корректировкой баланса — тоже атомарно.
     */
    suspend fun deleteOperation(operationId: Long) {
        db.withTransaction {
            val operation = wallet.findOperationById(operationId)
                ?: throw WalletException.AccountNotFound(operationId)
            if (operation.type == OperationType.SYSTEM) throw WalletException.ImmutableOperation()

            if (operation.status == OperationStatus.COMPLETED) {
                val account = wallet.findAccountById(operation.accountId)
                    ?: throw WalletException.AccountNotFound(operation.accountId)
                val newBalance = account.balanceCents - operation.amountCents
                if (newBalance < 0) {
                    throw WalletException.InsufficientFunds(account.balanceCents, -operation.amountCents)
                }
                wallet.updateBalance(account.id, newBalance, System.currentTimeMillis())
            }
            wallet.deleteOperation(operation)
        }
    }

    // ─────────────── Живые демонстрации ACID (учебный раздел) ───────────────

    /**
     * ⚠️ НАМЕРЕННО НЕПРАВИЛЬНЫЙ КОД — антипример для лаборатории.
     *
     * Один перевод разбит на ДВЕ независимые транзакции: сначала списание,
     * потом зачисление. Если между ними приложение падает (или его убивает
     * система), деньги списываются «в никуда»: сумма по всем счетам меняется,
     * и восстановить согласованность можно только компенсирующей операцией.
     *
     * Никогда так не делайте — сравните с [transfer], где обе половины
     * находятся в одной транзакции.
     */
    suspend fun brokenTransfer(fromAccountId: Long, toAccountId: Long, amountCents: Long): BrokenTransferReport {
        if (amountCents <= 0) throw WalletException.InvalidAmount()
        if (fromAccountId == toAccountId) throw WalletException.SameAccount()

        val totalBefore = totalAllAccountsCents()
        val fromBefore = wallet.findAccountById(fromAccountId)
            ?: throw WalletException.AccountNotFound(fromAccountId)
        val toBefore = wallet.findAccountById(toAccountId)
            ?: throw WalletException.AccountNotFound(toAccountId)

        var error: String? = null
        try {
            // ── Транзакция №1: списание. COMMIT происходит здесь же ──
            db.withTransaction {
                val from = wallet.findAccountById(fromAccountId)
                    ?: throw WalletException.AccountNotFound(fromAccountId)
                if (from.balanceCents < amountCents) {
                    throw WalletException.InsufficientFunds(from.balanceCents, amountCents)
                }
                val now = System.currentTimeMillis()
                wallet.updateBalance(from.id, from.balanceCents - amountCents, now)
                wallet.insertOperation(
                    OperationEntity(
                        accountId = from.id,
                        type = OperationType.TRANSFER_OUT,
                        amountCents = -amountCents,
                        status = OperationStatus.COMPLETED,
                        comment = "Разорванный перевод → «${toBefore.name}»",
                        groupId = UUID.randomUUID().toString(),
                        localSeq = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }

            // ── Между двумя транзакциями процесс погибает ──
            throw SimulatedGatewayFailure("приложение остановлено между двумя транзакциями")

            // ── Транзакция №2: зачисление. Сюда код уже не доходит ──
            // db.withTransaction { ... updateBalance(to) ... }
        } catch (t: SimulatedGatewayFailure) {
            error = t.reason
        }

        val fromAfter = wallet.findAccountById(fromAccountId)
        val toAfter = wallet.findAccountById(toAccountId)
        return BrokenTransferReport(
            fromName = fromBefore.name,
            toName = toBefore.name,
            amountCents = amountCents,
            failed = error != null,
            errorMessage = error,
            fromBeforeCents = fromBefore.balanceCents,
            fromAfterCents = fromAfter?.balanceCents ?: fromBefore.balanceCents,
            toBeforeCents = toBefore.balanceCents,
            toAfterCents = toAfter?.balanceCents ?: toBefore.balanceCents,
            totalBeforeCents = totalBefore,
            totalAfterCents = totalAllAccountsCents()
        )
    }

    /**
     * Стресс-тест изоляции: сразу [count] переводов в двух направлениях,
     * каждый — со своей настоящей транзакцией.
     *
     * Проверяем главное: сумма по всем счетам осталась прежней
     * (деньги только перекладывались), а в журнале появилось ровно
     * `2 × успешных` записи — ни одна половинка перевода не потерялась.
     */
    suspend fun parallelTransfers(
        fromAccountId: Long,
        toAccountId: Long,
        count: Int,
        amountCents: Long
    ): ParallelTransferReport {
        if (amountCents <= 0) throw WalletException.InvalidAmount()

        val fromBefore = wallet.findAccountById(fromAccountId)
            ?: throw WalletException.AccountNotFound(fromAccountId)
        val toBefore = wallet.findAccountById(toAccountId)
            ?: throw WalletException.AccountNotFound(toAccountId)
        val totalBefore = totalAllAccountsCents()
        val rowsBefore = wallet.countAllOperations()
        val started = System.nanoTime()

        val succeeded = AtomicInteger(0)
        val failed = AtomicInteger(0)

        coroutineScope {
            repeat(count) { index ->
                launch(Dispatchers.Default) {
                    // Чётные переводы идут «туда», нечётные — «обратно»: гонки гарантированы.
                    val (source, target) = if (index % 2 == 0) {
                        fromAccountId to toAccountId
                    } else {
                        toAccountId to fromAccountId
                    }
                    var attempt = 0
                    while (attempt < 3) {
                        attempt++
                        try {
                            transfer(source, target, amountCents, "Стресс-тест #${index + 1}")
                            succeeded.incrementAndGet()
                            return@launch
                        } catch (t: Throwable) {
                            // Блокировка записи — не ошибка данных: транзакция просто
                            // выполнится ещё раз. В реальном коде тут нужен backoff.
                            if (attempt >= 3) failed.incrementAndGet()
                        }
                    }
                }
            }
        }

        val duration = (System.nanoTime() - started) / 1_000_000
        val fromAfter = wallet.findAccountById(fromAccountId)
        val toAfter = wallet.findAccountById(toAccountId)

        // Диагностика целостности журнала: каждая успешная операция должна была
        // добавить ровно две записи (списание и зачисление).
        val rowsAdded = wallet.countAllOperations() - rowsBefore

        return ParallelTransferReport(
            requested = count,
            succeeded = succeeded.get(),
            failed = failed.get(),
            amountCents = amountCents,
            fromName = fromBefore.name,
            toName = toBefore.name,
            fromBeforeCents = fromBefore.balanceCents,
            fromAfterCents = fromAfter?.balanceCents ?: fromBefore.balanceCents,
            toBeforeCents = toBefore.balanceCents,
            toAfterCents = toAfter?.balanceCents ?: toBefore.balanceCents,
            totalBeforeCents = totalBefore,
            totalAfterCents = totalAllAccountsCents(),
            journalRowsAdded = rowsAdded,
            expectedJournalRows = succeeded.get() * 2,
            durationMs = duration
        )
    }

    // ─────────────────────── Инициализация и сброс ───────────────────────

    suspend fun seedIfEmpty() = seeder.seedIfEmpty()

    suspend fun resetEverything() = seeder.resetEverything()

    suspend fun ensureDemoAccounts(): List<AccountEntity> = seeder.ensureDemoAccounts()
}

/**
 * Отчёт о сверке журнала.
 * Пустой список [fixedAccounts] — это хорошая новость: инварианты сходятся.
 */
data class RecalculationReport(
    val checkedAccounts: Int,
    val fixedAccounts: List<String>
) {
    val isConsistent: Boolean get() = fixedAccounts.isEmpty()
}
