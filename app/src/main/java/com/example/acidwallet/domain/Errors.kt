package com.example.acidwallet.domain

/** Ошибки бизнес-логики. Все они ведут к откату транзакции (ROLLBACK). */
sealed class WalletException(message: String) : Exception(message) {

    /** Не хватает денег на счёте — проверка инварианта CONSISTENCY. */
    class InsufficientFunds(available: Long, requested: Long) : WalletException(
        "Недостаточно средств: на счёте ${Money.format(available)}, нужно ${Money.format(requested)}"
    )

    /** Счёт не найден (например, удалён другой транзакцией). */
    class AccountNotFound(id: Long) : WalletException("Счёт #$id не найден")

    /** Счёт заархивирован — операции по нему запрещены. */
    class AccountArchived(name: String) : WalletException("Счёт «$name» в архиве — операции запрещены")

    /** Сумма должна быть положительной. */
    class InvalidAmount : WalletException("Сумма должна быть больше нуля")

    /** Перевод на тот же счёт. */
    class SameAccount : WalletException("Нельзя перевести деньги на тот же счёт")

    /** Операция уже отменена. */
    class AlreadyCancelled : WalletException("Операция уже отменена")

    /** Системные записи отменять нельзя. */
    class ImmutableOperation : WalletException("Системные записи отменять нельзя")
}

/**
 * Искусственный сбой «внешнего платёжного шлюза».
 * Бросается ВНУТРИ транзакции, чтобы показать настоящий ROLLBACK SQLite.
 */
class SimulatedGatewayFailure(val reason: String) : Exception("Внешний шлюз не подтвердил операцию: $reason")
