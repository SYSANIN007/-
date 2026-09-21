package com.example.acidwallet.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Операция вместе с именем своего счёта — для общей ленты активности. */
data class OperationWithAccount(
    @Embedded val operation: OperationEntity,
    val accountName: String,
    val accountOwner: String
)

/**
 * DAO для «живого» блока Read на главном экране.
 *
 * Ключевой момент: все методы наблюдения возвращают Flow. Room пересчитывает
 * и повторно emits результат при КАЖДОМ изменении таблиц accounts/operations —
 * ровно поэтому UI сразу видит результат CRUD и транзакций.
 */
@Dao
interface IsolationDemoDao {

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert
    suspend fun insertOperation(operation: OperationEntity): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findAccount(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeAccount(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE isDemo = 1 ORDER BY id ASC")
    suspend fun findDemoAccounts(): List<AccountEntity>

    /** Наблюдение «свежайшего» изменения в БД — витрина возможностей Room. */
    @Query("SELECT * FROM accounts ORDER BY updatedAt DESC")
    fun observeAccountsByFreshness(): Flow<List<AccountEntity>>

    /** Количество строк в журнале — сюда же «стучится» атомарная транзакция. */
    @Query("SELECT COUNT(*) FROM operations WHERE accountId = :accountId")
    fun observeOperationCount(accountId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM operations WHERE accountId = :accountId")
    suspend fun countOperations(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM operations")
    fun observeTotalOperationCount(): Flow<Int>

    @Query(
        """
        SELECT o.*, a.name AS accountName, a.ownerName AS accountOwner
        FROM operations o
        INNER JOIN accounts a ON a.id = o.accountId
        WHERE a.isDemo = 0
        ORDER BY o.createdAt DESC, o.id DESC
        LIMIT :limit
        """
    )
    fun observeRecentActivity(limit: Int): Flow<List<OperationWithAccount>>

    @Query("UPDATE accounts SET balanceCents = :balanceCents, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, balanceCents: Long, updatedAt: Long)

    @Query("DELETE FROM accounts WHERE isDemo = 1")
    suspend fun deleteDemoAccounts()
}

/**
 * Операции для экспериментов с транзакциями.
 *
 * ⚠ Важно: здесь НЕТ методов, выполняющих бизнес-операцию целиком.
 * DAO умеет только элементарные шаги (прочитать баланс, обновить баланс,
 * вставить строку журнала). Склейка шагов в атомарную единицу — задача
 * репозитория, который оборачивает их в `db.withTransaction { ... }`.
 */
@Dao
interface TransactionDemoDao {

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert
    suspend fun insertOperation(operation: OperationEntity): Long

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findAccount(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name LIMIT 1")
    suspend fun findAccountByName(name: String): AccountEntity?

    @Query("SELECT balanceCents FROM accounts WHERE id = :id")
    suspend fun findBalance(id: Long): Long?

    @Query("SELECT COUNT(*) FROM operations WHERE accountId = :accountId")
    suspend fun countOperations(accountId: Long): Int

    @Query("UPDATE accounts SET balanceCents = :balanceCents, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, balanceCents: Long, updatedAt: Long)

    /** Сброс служебного счёта в исходное состояние перед опытом. */
    @Query(
        """
        UPDATE accounts
        SET balanceCents = :balanceCents,
            initialBalanceCents = :initialCents,
            updatedAt = :updatedAt
        WHERE id = :accountId
        """
    )
    suspend fun resetAccount(accountId: Long, balanceCents: Long, initialCents: Long, updatedAt: Long)

    @Query("DELETE FROM operations WHERE accountId = :accountId")
    suspend fun deleteOperationsForAccount(accountId: Long)
}
