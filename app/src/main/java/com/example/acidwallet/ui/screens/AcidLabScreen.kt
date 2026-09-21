package com.example.acidwallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.WalletViewModel
import com.example.acidwallet.ui.components.AtomicityReportCard
import com.example.acidwallet.ui.components.CodeBlock
import com.example.acidwallet.ui.components.HintBox
import com.example.acidwallet.ui.components.InlineCode
import com.example.acidwallet.ui.components.IsolationReportCard
import com.example.acidwallet.ui.components.KeyValueRow
import com.example.acidwallet.ui.components.PropertyHeader
import com.example.acidwallet.ui.components.ResultRow
import com.example.acidwallet.ui.components.SectionCard
import com.example.acidwallet.ui.components.SectionTitle
import com.example.acidwallet.ui.theme.AcidColors

/**
 * Вкладка «ACID» — теория и эксперименты.
 *
 * Здесь видно не только «что такое ACID», но и как каждое свойство
 * обеспечивается конкретным кодом этого приложения, и что происходит,
 * если транзакцию убрать.
 */
@Composable
fun AcidLabScreen(viewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val total by viewModel.totalBalance.collectAsStateWithLifecycle()
    val journalRows by viewModel.totalJournalRows.collectAsStateWithLifecycle()
    val atomicityReport by viewModel.atomicityReport.collectAsStateWithLifecycle()
    val isolationReport by viewModel.isolationReport.collectAsStateWithLifecycle()
    val recalc by viewModel.lastRecalc.collectAsStateWithLifecycle()
    val durabilityMarks by viewModel.durabilityMarks.collectAsStateWithLifecycle()
    val journalMode by viewModel.journalMode.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val stage by viewModel.stage.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ───────────────────────────── Введение ─────────────────────────────
        item {
            SectionCard {
                SectionTitle("ACID — четыре гарантии транзакций")
                Text(
                    "Транзакция — это группа изменений базы, которая для внешнего мира " +
                        "выглядит как одно неделимое действие. В этом приложении база " +
                        "локальная: SQLite + Room, а транзакции — настоящие (BEGIN … COMMIT/ROLLBACK).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                KeyValueRow("A · Atomicity", "всё или ничего: при исключении SQLite откатывает изменения")
                KeyValueRow("C · Consistency", "инварианты БД: баланс ≥ 0, баланс = начальный остаток + Σ журнала")
                KeyValueRow("I · Isolation", "одновременные транзакции сериализуются, «половинчатых» состояний не видно")
                KeyValueRow("D · Durability", "после COMMIT данные остаются в WAL-файле и переживут перезапуск")
            }
        }

        // ─────────────────────────── A. Atomicity ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Atomicity) {
                PropertyHeader(
                    letter = "A",
                    color = AcidColors.Atomicity,
                    title = "Atomicity — атомарность",
                    subtitle = "Изменения баланса и журнала применяются только вместе"
                )
                Text(
                    "Операции кошелька всегда пишут ДВЕ вещи: строку журнала и новый баланс. " +
                        "Если записать их двумя разными запросами, сбой между ними оставит базу " +
                        "в «полуприменённом» состоянии. Поэтому весь блок завёрнут в db.withTransaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeBlock(
                    """
                        // Так делает WalletRepository.transfer(...) — правильно:
                        db.withTransaction {
                            wallet.updateBalance(from.id, from.balanceCents - cents, now)
                            wallet.insertOperation(OperationEntity(TRANSFER_OUT, -cents, ...))
                            wallet.updateBalance(to.id, to.balanceCents + cents, now)
                            wallet.insertOperation(OperationEntity(TRANSFER_IN, +cents, ...))
                        }   // либо все четыре запроса, либо ни один
                    """.trimIndent()
                )
                Button(
                    onClick = { viewModel.runAtomicityDemo() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить эксперимент (3 шага)")
                }
                if (busy && stage != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stage ?: "", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Эксперимент выполняется на служебном счёте лаборатории: сначала корректная " +
                        "транзакция, затем тот же код БЕЗ транзакции с падением посередине, " +
                        "и наконец сбой внутри транзакции (ROLLBACK).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        atomicityReport?.let { report ->
            item { AtomicityReportCard(report) }
        }

        // ─────────────────────────── C. Consistency ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Consistency) {
                PropertyHeader(
                    letter = "C",
                    color = AcidColors.Consistency,
                    title = "Consistency — согласованность",
                    subtitle = "База переходит из одного корректного состояния в другое"
                )
                Text(
                    "В приложении есть три инварианта, которые проверяются прямо в коде:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KeyValueRow("1. Баланс не бывает отрицательным", "проверка перед списанием/переводом")
                KeyValueRow("2. Баланс = начальный остаток + Σ журнала", "журнал — источник правды")
                KeyValueRow("3. Операция не ссылается на несуществующий счёт", "FOREIGN KEY + ON DELETE CASCADE")
                Text(
                    "Инвариант №2 может быть пересчитан в любой момент — это и делает «Сверка журнала».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeBlock(
                    """
                        db.withTransaction {          // сверка целиком — одна транзакция
                            accounts.forEach { account ->
                                val expected = account.initialBalanceCents +
                                    dao.sumActiveOperations(account.id)
                                if (account.balanceCents != expected) {
                                    dao.updateBalance(account.id, expected)   // лечение
                                }
                            }
                        }
                    """.trimIndent(),
                    AcidColors.Consistency
                )
                FilledTonalButton(onClick = { viewModel.recalculate() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Проверить инварианты сейчас")
                }
                HorizontalDivider()
                KeyValueRow("Итого по счетам", Money.format(total))
                KeyValueRow("Счетов / строк журнала", "${accounts.size} / $journalRows")
                recalc?.let { report ->
                    ResultRow(
                        ok = report.isConsistent,
                        text = if (report.isConsistent) {
                            "Последняя сверка: проверено счетов — ${report.checkedAccounts}, " +
                                "расхождений нет. Инвариант сходится ✔"
                        } else {
                            "Последняя сверка: исправлено — ${report.fixedAccounts.size} из " +
                                "${report.checkedAccounts}: ${report.fixedAccounts.joinToString()}"
                        }
                    )
                }
            }
        }

        // ─────────────────────────── I. Isolation ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Isolation) {
                PropertyHeader(
                    letter = "I",
                    color = AcidColors.Isolation,
                    title = "Isolation — изоляция",
                    subtitle = "Параллельные транзакции не мешают друг другу"
                )
                Text(
                    "В приложении нет ни одного места, где баланс читается, а потом пишется " +
                        "«на стороне»: выборка и обновление всегда внутри одной транзакции. " +
                        "Иначе два одновременных изменения одного счёта затирают друг друга (lost update).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeBlock(
                    """
                        // ❌ потерянное обновление: между SELECT и UPDATE влезает другой поток
                        val current = dao.findBalance(id)
                        delay(40)
                        dao.updateBalance(id, current + 100_000)

                        // ✅ то же самое внутри транзакции — значения не «перепутаются»
                        db.withTransaction {
                            val current = dao.findBalance(id)
                            dao.updateBalance(id, current + 100_000)
                        }
                    """.trimIndent(),
                    AcidColors.Isolation
                )
                Button(
                    onClick = { viewModel.runIsolationDemo() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить 4 опыта с изоляцией")
                }
                if (busy && stage != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stage ?: "", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Опыты идут на служебных счетах A и B по 10 000 ₽: чтение без транзакции, " +
                        "чтение в транзакции, потерянное обновление и его исправленная версия. " +
                        "Живые демонстрации на своих счетах — во вкладке «Демо-банк».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        isolationReport?.let { report ->
            item { IsolationReportCard(report) }
        }

        // ─────────────────────────── D. Durability ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Durability) {
                PropertyHeader(
                    letter = "D",
                    color = AcidColors.Durability,
                    title = "Durability — долговечность",
                    subtitle = "После COMMIT данные уже никто не потеряет"
                )
                Text(
                    "База работает в режиме журнала ${journalMode.uppercase()}: изменения сначала " +
                        "пишутся в WAL-файл и только потом (чекпоинтом) переносятся в основную базу. " +
                        "Именно поэтому COMMIT означает «данные на диске», а не «данные в памяти».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CodeBlock(
                    """
                        Room.databaseBuilder(context, AppDatabase::class.java, "acid_wallet.db")
                            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)  // WAL
                            .addCallback(object : Callback() {
                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    db.execSQL("PRAGMA foreign_keys = ON")
                                }
                            })
                            .build()
                    """.trimIndent(),
                    AcidColors.Durability
                )
                HorizontalDivider()
                KeyValueRow("Отметок долговечности в базе", durabilityMarks.toString(), emphasize = true)
                Button(onClick = { viewModel.leaveDurabilityMark() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Оставить отметку в журнале")
                }
                HintBox(
                    "Проверьте сами: нажмите кнопку, затем полностью закройте приложение " +
                        "(свайпом из списка недавних) и откройте снова. Количество отметок сохранится — " +
                        "это и есть свойство D. Отметки пишутся в служебный журнал проверок, " +
                        "пользовательские данные при этом не трогаются.",
                    AcidColors.Durability
                )
            }
        }

        // ─────────────────────────── Как это устроено ───────────────────────────
        item {
            SectionCard {
                SectionTitle("Как устроен код")
                KeyValueRow("Room + SQLite", "таблицы accounts и operations, FOREIGN KEY с CASCADE")
                KeyValueRow("WalletDao", "все четыре буквы CRUD: @Insert, @Query/@Relation, @Update, @Delete")
                KeyValueRow("WalletRepository", "бизнес-логика: каждая операция = одна транзакция")
                KeyValueRow("AtomicityLab", "эксперименты: COMMIT, «полуприменённое» состояние, ROLLBACK")
                KeyValueRow("IsolationLab", "4 опыта с гонками на служебных счетах A и B")
                KeyValueRow("Compose UI", "все данные приходят как Flow — интерфейс обновляется сам")
                HorizontalDivider()
                Text(
                    "Откройте в Android Studio файл WalletRepository.kt: там в каждом методе видно, " +
                        "какие именно запросы объединены в транзакцию. Рядом лежит пример кода " +
                        "transfer, который показывает, как выглядит «всё или ничего» на практике.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Подсказка: ", style = MaterialTheme.typography.bodySmall)
                    InlineCode("db.withTransaction { }")
                }
                Text(
                    "— именно этот вызов превращает набор запросов в атомарную транзакцию.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}
