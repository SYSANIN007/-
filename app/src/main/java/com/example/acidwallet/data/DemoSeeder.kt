package com.example.acidwallet.data

import androidx.room.withTransaction
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType

/**
 * Наполнение базы демонстрационными данными.
 *
 * Обратите внимание: весь набор строк вставляется ВНУТРИ одной транзакции
 * (`db.withTransaction`). Если приложение упадёт посреди наполнения,
 * в базе не останется «полупустого» состояния — либо всё, либо ничего.
 */
class DemoSeeder(private val db: AppDatabase) {

    private val wallet = db.walletDao()

    /** Имена служебных счетов, на которых работает ACID-лаборатория. */
    companion object {
        const val DEMO_A_NAME = "🧪 Демо-счёт A (лаборатория)"
        const val DEMO_B_NAME = "🧪 Демо-счёт B (лаборатория)"
        const val DEMO_TX_NAME = "🧪 Демо-счёт транзакций"
        const val DURABILITY_NAME = "🧾 Журнал проверок долговечности"

        /** Стартовые балансы демо-счетов (в копейках). */
        const val DEMO_START_CENTS = 1_000_000L   // 10 000 ₽
    }

    /** Наполняет базу при первом запуске. */
    suspend fun seedIfEmpty() {
        if (wallet.countUserAccounts() > 0) return
        seedUserData()
        ensureDemoAccounts()
    }

    /** Полный сброс: пользовательские данные + лабораторные счета заново. */
    suspend fun resetEverything() = db.withTransaction {
        wallet.deleteAllOperations()
        wallet.deleteAllAccounts()
    }.let {
        seedUserData()
        ensureDemoAccounts()
    }

    /** Создаёт (или пересоздаёт) служебные счета ACID-лаборатории. */
    suspend fun resetDemoAccounts() = db.withTransaction {
        wallet.deleteDemoAccounts()          // журнал уходит каскадом
        insertDemoAccount(DEMO_A_NAME, DEMO_START_CENTS, sortOrder = 90)
        insertDemoAccount(DEMO_B_NAME, DEMO_START_CENTS, sortOrder = 91)
        insertDemoAccount(DEMO_TX_NAME, 0L, sortOrder = 92)
        Unit
    }

    /**
     * Лабораторные счета должны существовать всегда: если пользователь их
     * случайно удалит, демо-режимы снова их создадут.
     */
    suspend fun ensureDemoAccounts(): List<AccountEntity> {
        val demoAccounts = db.isolationDao().findDemoAccounts()
        val haveA = demoAccounts.any { it.name == DEMO_A_NAME }
        val haveB = demoAccounts.any { it.name == DEMO_B_NAME }
        val haveTx = demoAccounts.any { it.name == DEMO_TX_NAME }
        if (haveA && haveB && haveTx) return demoAccounts

        db.withTransaction {
            if (!haveA) insertDemoAccount(DEMO_A_NAME, DEMO_START_CENTS, 90)
            if (!haveB) insertDemoAccount(DEMO_B_NAME, DEMO_START_CENTS, 91)
            if (!haveTx) insertDemoAccount(DEMO_TX_NAME, 0L, 92)
        }
        return db.isolationDao().findDemoAccounts()
    }

    /** Три демонстрационных кошелька с уже существующей историей операций. */
    private suspend fun seedUserData() = db.withTransaction {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        // ── Счёт 1: основная карта ──────────────────────────────────────────
        val cardId = wallet.insertAccount(
            AccountEntity(
                name = "Основная карта",
                ownerName = "Студент",
                type = AccountType.BANK_CARD,
                balanceCents = 0L,
                initialBalanceCents = 0L,
                note = "Зарплата и повседневные траты",
                sortOrder = 0,
                createdAt = now - 30 * day,
                updatedAt = now - day
            )
        )
        val cardOps = listOf(
            Triple(OperationType.DEPOSIT, 5_000_000L, "Зарплата"),
            Triple(OperationType.WITHDRAWAL, -1_850_000L, "Аренда квартиры"),
            Triple(OperationType.WITHDRAWAL, -420_000L, "Продукты"),
            Triple(OperationType.TRANSFER_OUT, -150_000L, "Перевод другу")
        )
        cardOps.forEachIndexed { index, (type, amount, comment) ->
            wallet.insertOperation(
                OperationEntity(
                    accountId = cardId,
                    type = type,
                    amountCents = amount,
                    status = OperationStatus.COMPLETED,
                    comment = comment,
                    localSeq = index,
                    createdAt = now - (cardOps.size - index) * day,
                    updatedAt = now - (cardOps.size - index) * day
                )
            )
        }
        // Баланс = начальный остаток + сумма журнала; начальный остаток подбираем так,
        // чтобы итог был ровным демонстрационным числом.
        val cardSum = cardOps.sumOf { it.second }
        val cardOpening = 4_235_000L - cardSum
        wallet.updateBalance(cardId, 4_235_000L, now)
        setInitialBalance(cardId, cardOpening)

        // ── Счёт 2: копилка ────────────────────────────────────────────────
        val savingsId = wallet.insertAccount(
            AccountEntity(
                name = "Копилка на отпуск",
                ownerName = "Студент",
                type = AccountType.SAVINGS,
                balanceCents = 0L,
                initialBalanceCents = 0L,
                note = "Откладываем 10% с каждой стипендии",
                sortOrder = 1,
                createdAt = now - 90 * day,
                updatedAt = now - 3 * day
            )
        )
        val savingsOps = listOf(
            Triple(OperationType.DEPOSIT, 600_000L, "Стипендия → копилка"),
            Triple(OperationType.DEPOSIT, 200_000L, "Подработка → копилка")
        )
        savingsOps.forEachIndexed { index, (type, amount, comment) ->
            wallet.insertOperation(
                OperationEntity(
                    accountId = savingsId,
                    type = type,
                    amountCents = amount,
                    status = OperationStatus.COMPLETED,
                    comment = comment,
                    localSeq = index,
                    createdAt = now - (savingsOps.size - index) * 3 * day,
                    updatedAt = now - (savingsOps.size - index) * 3 * day
                )
            )
        }
        val savingsOpening = 1_500_000L - savingsOps.sumOf { it.second }
        wallet.updateBalance(savingsId, 1_500_000L, now)
        setInitialBalance(savingsId, savingsOpening)

        // ── Счёт 3: наличные ──────────────────────────────────────────────
        val cashId = wallet.insertAccount(
            AccountEntity(
                name = "Наличные",
                ownerName = "Студент",
                type = AccountType.CASH,
                balanceCents = 0L,
                initialBalanceCents = 0L,
                note = "То, что в кошельке",
                sortOrder = 2,
                createdAt = now - 150 * day,
                updatedAt = now - 2 * day
            )
        )
        wallet.insertOperation(
            OperationEntity(
                accountId = cashId,
                type = OperationType.DEPOSIT,
                amountCents = 820_000L,
                status = OperationStatus.COMPLETED,
                comment = "Снятие с карты",
                localSeq = 0,
                createdAt = now - 2 * day,
                updatedAt = now - 2 * day
            )
        )
        wallet.updateBalance(cashId, 820_000L, now)
        setInitialBalance(cashId, 0L)
    }

    private suspend fun setInitialBalance(accountId: Long, initialCents: Long) {
        val account = wallet.findAccountById(accountId) ?: return
        wallet.updateAccount(account.copy(initialBalanceCents = initialCents))
    }

    private suspend fun insertDemoAccount(name: String, balanceCents: Long, sortOrder: Int): Long {
        val now = System.currentTimeMillis()
        return wallet.insertAccount(
            AccountEntity(
                name = name,
                ownerName = "ACID-лаборатория",
                type = AccountType.CRYPTO,
                balanceCents = balanceCents,
                initialBalanceCents = balanceCents,
                note = "Служебный счёт для демонстрации транзакций",
                isDemo = true,
                sortOrder = sortOrder,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}
