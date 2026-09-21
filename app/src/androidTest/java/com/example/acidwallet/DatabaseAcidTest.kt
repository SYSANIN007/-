package com.example.acidwallet

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.acidwallet.data.WalletRepository
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.domain.WalletException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Инструментальные тесты базы данных.
 *
 * Запуск: правый клик по файлу → Run (нужен эмулятор или подключённый телефон).
 *
 * Тесты проверяют то, что в приложении подаётся как «ACID»:
 * все четыре буквы — на настоящей SQLite (in-memory база Room).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseAcidTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: WalletRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Та же конфигурация, что и в приложении: внешние ключи включены.
        db = AppDatabase.build(context, inMemory = true)
        repo = WalletRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─────────────────────────────── CRUD ───────────────────────────────

    @Test
    fun crudCreateReadUpdateDelete() = runBlocking {
        val id = repo.createAccount("Тестовый счёт", "Студент", AccountType.BANK_CARD, 100_000, "заметка")

        // READ
        val created = db.walletDao().findAccountById(id)
        assertNotNull("Счёт должен создаться (CREATE)", created)
        assertEquals(100_000L, created!!.balanceCents)
        assertEquals(1, db.walletDao().countOperations(id))   // системная запись «Начальный остаток»

        // UPDATE
        repo.updateAccount(id, "Переименованный", "Владелец", AccountType.SAVINGS, "новая заметка", false)
        val updated = db.walletDao().findAccountById(id)!!
        assertEquals("Переименованный", updated.name)
        assertEquals(AccountType.SAVINGS, updated.type)
        assertFalse("Счёт ушёл в архив", updated.isActive)
        assertEquals("Баланс при правке паспортных данных не меняется", 100_000L, updated.balanceCents)

        // DELETE
        repo.deleteAccount(id)
        assertNull("Счёт должен удалиться (DELETE)", db.walletDao().findAccountById(id))
    }

    @Test
    fun deleteAccountCascadesJournal() = runBlocking {
        val id = repo.createAccount("Счёт с журналом", "Студент", AccountType.CASH, 50_000, "")
        repo.deposit(id, 10_000, "Пополнение")
        repo.withdraw(id, 5_000, "Списание")
        assertTrue(db.walletDao().countOperations(id) >= 3)

        repo.deleteAccount(id)

        assertEquals(
            "Операции удалённого счёта должны уйти каскадом (ON DELETE CASCADE)",
            0,
            db.walletDao().countAllOperations()
        )
    }

    // ─────────────────────────── A. Atomicity ───────────────────────────

    @Test
    fun transferChangesBothAccountsInOneTransaction() = runBlocking {
        val a = repo.createAccount("A", "Студент", AccountType.BANK_CARD, 100_000, "")
        val b = repo.createAccount("B", "Студент", AccountType.SAVINGS, 100_000, "")
        val totalBefore = db.walletDao().totalBalanceAllAccountsCents()

        repo.transfer(a, b, 30_000, "Тестовый перевод")

        assertEquals(70_000L, db.walletDao().findAccountById(a)!!.balanceCents)
        assertEquals(130_000L, db.walletDao().findAccountById(b)!!.balanceCents)
        assertEquals(
            "Сумма по всем счетам не должна измениться",
            totalBefore,
            db.walletDao().totalBalanceAllAccountsCents()
        )
        // Один перевод = две записи журнала (списание и зачисление)
        assertEquals(1, db.walletDao().countOperations(a))   // TRANSFER_OUT
        assertEquals(2, db.walletDao().countOperations(b))   // «Начальный остаток» + TRANSFER_IN
    }

    @Test
    fun failedTransferRollsBackCompletely() = runBlocking {
        val a = repo.createAccount("A", "Студент", AccountType.BANK_CARD, 10_000, "")
        val b = repo.createAccount("B", "Студент", AccountType.SAVINGS, 10_000, "")
        val rowsBefore = db.walletDao().countAllOperations()

        try {
            repo.transfer(a, b, 999_000, "Слишком большая сумма")
            fail("Перевод без достаточного баланса обязан упасть")
        } catch (expected: WalletException.InsufficientFunds) {
            // ожидаемо
        }

        assertEquals("Баланс отправителя не изменился", 10_000L, db.walletDao().findAccountById(a)!!.balanceCents)
        assertEquals("Баланс получателя не изменился", 10_000L, db.walletDao().findAccountById(b)!!.balanceCents)
        assertEquals(
            "Журнал не должен пополниться: транзакция откатилась целиком",
            rowsBefore,
            db.walletDao().countAllOperations()
        )
    }

    @Test
    fun exceptionInsideTransactionRollsBackEverything() = runBlocking {
        val accountId = repo.createAccount("A", "Студент", AccountType.CASH, 10_000, "")
        val rowsBefore = db.walletDao().countAllOperations()
        val balanceBefore = db.walletDao().findAccountById(accountId)!!.balanceCents

        try {
            db.withTransaction {
                db.walletDao().insertOperation(
                    OperationEntity(
                        accountId = accountId,
                        type = OperationType.DEPOSIT,
                        amountCents = 500_000,
                        status = OperationStatus.COMPLETED,
                        comment = "Эта запись не должна сохраниться"
                    )
                )
                val current = db.walletDao().findAccountById(accountId)!!.balanceCents
                db.walletDao().updateBalance(accountId, current + 500_000, System.currentTimeMillis())
                throw IllegalStateException("Искусственный сбой внутри транзакции")
            }
        } catch (expected: IllegalStateException) {
            // ожидаемо: Room сделал ROLLBACK
        }

        assertEquals(rowsBefore, db.walletDao().countAllOperations())
        assertEquals(balanceBefore, db.walletDao().findAccountById(accountId)!!.balanceCents)
    }

    // ────────────────────────── C. Consistency ──────────────────────────

    @Test
    fun cancelOperationRestoresBalanceAndKeepsAuditRow() = runBlocking {
        val id = repo.createAccount("A", "Студент", AccountType.BANK_CARD, 100_000, "")
        repo.withdraw(id, 30_000, "Покупка")
        assertEquals(70_000L, db.walletDao().findAccountById(id)!!.balanceCents)

        val operation = db.walletDao().getAccountsWithOperations()
            .first { entry -> entry.account.id == id }
            .operations
            .first { op -> op.type == OperationType.WITHDRAWAL }

        repo.cancelOperation(operation.id)

        assertEquals("Баланс вернулся к прежнему значению", 100_000L, db.walletDao().findAccountById(id)!!.balanceCents)
        assertEquals(
            "Запись остаётся в журнале как аудит-лог",
            OperationStatus.CANCELLED,
            db.walletDao().findOperationById(operation.id)!!.status
        )
        assertTrue(invariantHolds(id))
    }

    @Test
    fun recalculateFixesBrokenBalance() = runBlocking {
        val id = repo.createAccount("A", "Студент", AccountType.CASH, 100_000, "")
        repo.deposit(id, 25_000, "Пополнение")

        // Ломаем баланс «в обход» журнала — так делать нельзя, но так бывает при сбоях.
        db.walletDao().updateBalance(id, 1L, System.currentTimeMillis())
        assertFalse(invariantHolds(id))

        val report = repo.recalculateBalances()

        assertTrue(report.isConsistent)
        assertEquals("Сверка восстановила баланс по журналу", 125_000L, db.walletDao().findAccountById(id)!!.balanceCents)
        assertTrue(invariantHolds(id))
    }

    @Test
    fun journalKeepsPairedRecordsWithSameGroupId() = runBlocking {
        val a = repo.createAccount("A", "Студент", AccountType.BANK_CARD, 100_000, "")
        val b = repo.createAccount("B", "Студент", AccountType.SAVINGS, 100_000, "")
        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        db.withTransaction {
            db.walletDao().updateBalance(a, 70_000, now)
            db.walletDao().insertOperation(
                OperationEntity(
                    accountId = a, type = OperationType.TRANSFER_OUT, amountCents = -30_000,
                    status = OperationStatus.COMPLETED, comment = "Перевод", groupId = groupId, localSeq = 0
                )
            )
            db.walletDao().updateBalance(b, 130_000, now)
            db.walletDao().insertOperation(
                OperationEntity(
                    accountId = b, type = OperationType.TRANSFER_IN, amountCents = 30_000,
                    status = OperationStatus.COMPLETED, comment = "Перевод", groupId = groupId, localSeq = 1
                    )
            )
        }

        val group = db.walletDao().getAccountsWithOperations()
            .flatMap { it.operations }
            .filter { it.groupId == groupId }
        assertEquals("Пара записей одного перевода", 2, group.size)
        assertEquals(0L, group.sumOf { it.amountCents })   // −30 000 + 30 000
    }

    /** Инвариант CONSISTENCY: баланс = начальный остаток + Σ проведённых операций. */
    private suspend fun invariantHolds(accountId: Long): Boolean {
        val account = db.walletDao().findAccountById(accountId) ?: return false
        val sum = db.walletDao().sumActiveOperations(accountId)
        return account.balanceCents == account.initialBalanceCents + sum
    }
}
