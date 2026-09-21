package com.example.acidwallet.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import kotlinx.coroutines.flow.Flow

/**
 * Полный CRUD над счетами и журналом операций.
 *
 *   C — insertAccount / insertOperation
 *   R — observeAccountsWithOperations / findAccountById / searchOperations
 *   U — updateAccount / updateOperation / patchOperation
 *   D — deleteAccount / deleteOperation / clearAll
 *
 * Обратите внимание: методы, меняющие сразу несколько строк, помечены
 * @Transaction — это ручная настройка атомарности на уровне DAO.
 */
@Dao
interface WalletDao {

    // ────────────────────────────── CREATE ──────────────────────────────

    @Insert
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert
    suspend fun insertOperation(operation: OperationEntity): Long

    @Insert
    suspend fun insertOperations(operations: List<OperationEntity>)

    // ─────────────────────────────── READ ───────────────────────────────

    /** Основной список: счета и их журналы одним запросом, реактивно (Flow). */
    @Transaction
    @Query("SELECT * FROM accounts WHERE isDemo = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAccountsWithOperations(): Flow<List<AccountWithOperations>>

    /** Только «скелет» счетов — для быстрых расчётов и демо-режимов. */
    @Transaction
    @Query("SELECT * FROM accounts WHERE isDemo = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun getAccountsWithOperations(): List<AccountWithOperations>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findAccountById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isDemo = 0 AND id = :id")
    suspend fun findVisibleAccount(id: Long): AccountEntity?

    @Query("SELECT * FROM operations WHERE id = :id")
    suspend fun findOperationById(id: Long): OperationEntity?

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    /** Сумма балансов ВСЕХ счетов, включая служебные демо-счета — проверка инварианта. */
    @Query("SELECT IFNULL(SUM(balanceCents), 0) FROM accounts")
    suspend fun totalBalanceAllAccountsCents(): Long

    /** Сумма балансов только пользовательских счетов — «Итого» на главном экране. */
    @Query("SELECT IFNULL(SUM(balanceCents), 0) FROM accounts WHERE isDemo = 0")
    fun observeTotalBalanceCents(): Flow<Long>

    @Query("SELECT COUNT(*) FROM accounts WHERE isDemo = 0")
    suspend fun countUserAccounts(): Int

    @Query("SELECT COUNT(*) FROM operations WHERE accountId = :accountId")
    suspend fun countOperations(accountId: Long): Int

    /** Всего строк в журнале — проверка «ни одна половинка перевода не потерялась». */
    @Query("SELECT COUNT(*) FROM operations")
    suspend fun countAllOperations(): Int

    /** Сумма журнала по счёту — все записи, включая отменённые (для справок). */
    @Query("SELECT IFNULL(SUM(amountCents), 0) FROM operations WHERE accountId = :accountId")
    suspend fun sumOperations(accountId: Long): Long

    /**
     * Сумма журнала без отменённых операций — именно это значение участвует
     * в инварианте `баланс == начальный остаток + Σ(проведённых операций)`.
     */
    @Query("SELECT IFNULL(SUM(amountCents), 0) FROM operations WHERE accountId = :accountId AND status <> 'CANCELLED'")
    suspend fun sumActiveOperations(accountId: Long): Long

    /**
     * READ + фильтры: поиск по комментарию, типу и статусу.
     * Параметры допускают NULL — тогда условие не применяется.
     */
    @Query(
        """
        SELECT o.* FROM operations o
        INNER JOIN accounts a ON a.id = o.accountId
        WHERE a.isDemo = 0
          AND (:accountId IS NULL OR o.accountId = :accountId)
          AND (:query = '' OR o.comment LIKE '%' || :query || '%')
          AND (:typeName IS NULL OR o.type = :typeName)
          AND (:statusName IS NULL OR o.status = :statusName)
        ORDER BY o.createdAt DESC, o.id DESC
        LIMIT :limit
        """
    )
    fun observeOperationsFiltered(
        accountId: Long?,
        query: String,
        typeName: String?,
        statusName: String?,
        limit: Int
    ): Flow<List<OperationEntity>>

    // ─────────────────────────────── UPDATE ─────────────────────────────

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Update
    suspend fun updateOperation(operation: OperationEntity)

    /** Точечное обновление баланса — самый частый UPDATE в приложении. */
    @Query("UPDATE accounts SET balanceCents = :balanceCents, updatedAt = :updatedAt WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, balanceCents: Long, updatedAt: Long)

    /** Частичное обновление: Room генерирует UPDATE только для указанных колонок. */
    @Query(
        """
        UPDATE operations
        SET comment = :comment,
            amountCents = :amountCents,
            status = :status,
            updatedAt = :updatedAt
        WHERE id = :operationId
        """
    )
    suspend fun patchOperation(operationId: Long, comment: String, amountCents: Long, status: OperationStatus, updatedAt: Long)

    @Query("UPDATE operations SET status = :status, updatedAt = :updatedAt WHERE groupId = :groupId")
    suspend fun updateStatusByGroup(groupId: String, status: OperationStatus, updatedAt: Long)

    // ─────────────────────────────── DELETE ─────────────────────────────

    /** Удаление счёта. Журнал операций уходит каскадом (ON DELETE CASCADE). */
    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Delete
    suspend fun deleteOperation(operation: OperationEntity)

    @Query("DELETE FROM operations WHERE groupId = :groupId")
    suspend fun deleteOperationsByGroup(groupId: String)

    @Query("DELETE FROM accounts WHERE isDemo = 1")
    suspend fun deleteDemoAccounts()

    @Query("DELETE FROM operations")
    suspend fun deleteAllOperations()

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()
}
