package com.example.acidwallet.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Локальная база данных приложения (SQLite + Room).
 *
 * Настройки, которые напрямую влияют на ACID:
 *  ▸ journalMode = WRITE_AHEAD_LOGGING — читатели не блокируют писателя,
 *    а изменения сначала попадают в WAL-файл (это и есть «мгновенная»
 *    долговечность с последующим чекпоинтом в основной файл);
 *  ▸ PRAGMA foreign_keys = ON — включена проверка внешних ключей,
 *    поэтому ON DELETE CASCADE реально работает;
 *  ▸ PRAGMA synchronous (по умолчанию NORMAL для WAL) — компромисс между
 *    скоростью и гарантией сохранности данных при выключении питания.
 */
@Database(
    entities = [AccountEntity::class, OperationEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun walletDao(): WalletDao
    abstract fun isolationDao(): IsolationDemoDao
    abstract fun transactionDemoDao(): TransactionDemoDao

    /** Текущий режим журнала SQLite — показываем его в карточке Durability. */
    fun journalMode(): String = try {
        openHelper.writableDatabase.query("PRAGMA journal_mode").use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else "unknown"
        }
    } catch (t: Throwable) {
        "unknown"
    }

    companion object {
        private const val DB_NAME = "acid_wallet.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** Единственный экземпляр базы на всё приложение. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        /**
         * Настройка базы. Одни и те же правила используются и в приложении,
         * и в инструментальных тестах — иначе тесты проверяли бы «не ту» базу.
         */
        fun build(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            }
            if (!inMemory) {
                // WAL: читатели не блокируют писателя, а COMMIT означает запись в файл журнала.
                builder.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            }
            return builder
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Без этого ON DELETE CASCADE не сработает.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
        }
    }
}
