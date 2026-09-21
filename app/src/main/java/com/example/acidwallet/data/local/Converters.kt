package com.example.acidwallet.data.local

import androidx.room.TypeConverter
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType

/**
 * Конвертеры типов для Room: перечисления хранятся в SQLite как TEXT.
 * @TypeConverters объявлен один раз в AppDatabase и действует на всю базу.
 */
class Converters {

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType = AccountType.fromName(value)

    @TypeConverter
    fun operationTypeToString(value: OperationType): String = value.name

    @TypeConverter
    fun stringToOperationType(value: String): OperationType = OperationType.fromName(value)

    @TypeConverter
    fun operationStatusToString(value: OperationStatus): String = value.name

    @TypeConverter
    fun stringToOperationStatus(value: String): OperationStatus = OperationStatus.fromName(value)
}
