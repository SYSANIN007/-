package com.example.acidwallet.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded

/**
 * Сущности Room — это и есть схема таблиц SQLite.
 *
 * ▸ accounts  — счета (кошельки). CRUD выполняется над ними.
 * ▸ operations — журнал операций. Ключ ACID-гарантий: журнал неизменяем
 *   (append-only) и связан со счётом внешним ключом с правилом CASCADE.
 */

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["ownerName"])]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Название счёта, например «Основная карта». */
    val name: String,

    /** Владелец счёта — атрибут, который видно в CRUD-форме. */
    val ownerName: String,

    /** Тип счёта. Хранится как TEXT через TypeConverter. */
    val type: com.example.acidwallet.data.model.AccountType,

    /** Валюта счёта — на будущее (мультивалютность). */
    val currency: String = "RUB",

    /** Текущий баланс в копейках. Деньги считаем в целых числах — никаких Double! */
    val balanceCents: Long,

    /**
     * «Начальный» остаток на момент создания счёта.
     * Инвариант БД: balanceCents == initialBalanceCents + Σ(проведённых операций).
     * Его проверяет кнопка «Сверка журнала» в ACID-лаборатории.
     */
    val initialBalanceCents: Long,

    /** Счёт активен (не архивирован). */
    val isActive: Boolean = true,

    /** Произвольная заметка пользователя. */
    val note: String = "",

    /** Служебные счета для демонстрации ACID-экспериментов (скрыты в списке кошелька). */
    val isDemo: Boolean = false,

    /** Порядок отображения. */
    val sortOrder: Int = 0,

    /** Время создания (UTC, миллисекунды). */
    val createdAt: Long = System.currentTimeMillis(),

    /** Время последнего изменения — видно в CRUD-списке. */
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "operations",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,   // удалили счёт → журнал уходит вместе с ним
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId"]), Index(value = ["createdAt"])]
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Ссылка на счёт — внешний ключ на accounts.id. */
    val accountId: Long,

    val type: com.example.acidwallet.data.model.OperationType,

    /** Сумма со знаком: плюс — приход, минус — расход. В копейках. */
    val amountCents: Long,

    val status: com.example.acidwallet.data.model.OperationStatus,

    /** Комментарий, который вводит пользователь. */
    val comment: String = "",

    /** Уникальный идентификатор серии операций (например, один перевод = две записи). */
    val groupId: String? = null,

    /**
     * Порядковый номер внутри серии (перевод создаёт две записи: списание и зачисление).
     * Нужен для строгого порядка внутри одной серии, где время совпадает до миллисекунд.
     */
    val localSeq: Int = 0,

    /** Момент операции. */
    val createdAt: Long = System.currentTimeMillis(),

    /** Момент последней правки записи (для CRUD-обновлений). */
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Счёт вместе с его операциями.
 * Room соберёт этот объект одним запросом с помощью @Relation.
 */
data class AccountWithOperations(
    @Embedded val account: AccountEntity,
    @Relation(parentColumn = "id", entityColumn = "accountId")
    val operations: List<OperationEntity>
) {
    /** Сумма всех операций журнала (без учёта начального остатка). */
    val operationsSumCents: Long get() = operations.sumOf { it.amountCents }
}
