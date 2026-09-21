package com.example.acidwallet.data.model

/**
 * Доменные модели приложения «ACID Wallet».
 *
 * Здесь описаны перечисления и «плоские» модели, которыми пользуется UI.
 * Хранение в SQLite выполняют сущности Room (см. пакет `data.local`).
 */

/** Тип счёта (кошелька). */
enum class AccountType(val label: String, val emoji: String) {
    CASH("Наличные", "💵"),
    BANK_CARD("Банковская карта", "💳"),
    SAVINGS("Копилка", "🏦"),
    CRYPTO("Крипто-кошелёк", "🪙");

    companion object {
        /** Безопасный разбор значения из БД. */
        fun fromName(name: String?): AccountType =
            entries.firstOrNull { it.name == name } ?: BANK_CARD
    }
}

/** Тип операции в журнале. */
enum class OperationType(val label: String, val isIncome: Boolean) {
    DEPOSIT("Пополнение", true),
    WITHDRAWAL("Списание", false),
    TRANSFER_IN("Входящий перевод", true),
    TRANSFER_OUT("Исходящий перевод", false),
    SYSTEM("Системная операция", true);

    companion object {
        fun fromName(name: String?): OperationType =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/** Статус операции. Отменённая операция остаётся в журнале — это аудит-лог. */
enum class OperationStatus(val label: String) {
    COMPLETED("Проведена"),
    CANCELLED("Отменена"),
    PENDING("В обработке");

    companion object {
        fun fromName(name: String?): OperationStatus =
            entries.firstOrNull { it.name == name } ?: COMPLETED
    }
}

/** Запись о счёте — «проекция» сущности Room для отчётов демо-режимов. */
data class AccountRecord(
    val id: Long,
    val name: String,
    val ownerName: String,
    val type: AccountType,
    val balanceCents: Long,
    val isActive: Boolean
)

/** Результат замера балансов до/после: наглядно показывает изменение денег. */
data class BalanceSnapshot(
    val accountId: Long,
    val accountName: String,
    val balanceBeforeCents: Long,
    val balanceAfterCents: Long
) {
    val deltaCents: Long get() = balanceAfterCents - balanceBeforeCents
}

/** Статус операции в «человеческом» виде — для бейджа в журнале. */
fun OperationStatus.isFinal(): Boolean = this != OperationStatus.PENDING
