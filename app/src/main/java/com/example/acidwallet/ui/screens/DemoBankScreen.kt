package com.example.acidwallet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.WalletViewModel
import com.example.acidwallet.ui.components.Badge
import com.example.acidwallet.ui.components.BrokenTransferReportCard
import com.example.acidwallet.ui.components.CodeBlock
import com.example.acidwallet.ui.components.HintBox
import com.example.acidwallet.ui.components.KeyValueRow
import com.example.acidwallet.ui.components.MoneyText
import com.example.acidwallet.ui.components.ParallelTransferReportCard
import com.example.acidwallet.ui.components.ResultRow
import com.example.acidwallet.ui.components.SectionCard
import com.example.acidwallet.ui.components.SectionTitle
import com.example.acidwallet.ui.formatShort
import com.example.acidwallet.ui.theme.AcidColors

/**
 * «Демо-банк» — живые ACID-опыты на обычных счетах пользователя.
 *
 * Здесь видно, как одна и та же операция выглядит в правильном коде
 * (одна транзакция) и в неправильном (две транзакции, «разорванный» перевод),
 * и что при этом происходит с суммой денег на экране.
 */
@Composable
fun DemoBankScreen(viewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val bankAccounts by viewModel.bankAccounts.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val brokenReport by viewModel.brokenReport.collectAsStateWithLifecycle()
    val parallelReport by viewModel.parallelReport.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val stage by viewModel.stage.collectAsStateWithLifecycle()

    val bankTotal = bankAccounts.sumOf { it.account.balanceCents }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                SectionTitle("Живая демонстрация на ваших счетах")
                Text(
                    "Дальше работают самые обычные счета из кошелька — те же, что и во вкладке «Кошелёк». " +
                        "Следите за строкой «Сумма A + B»: деньги можно перекладывать между счетами, " +
                        "но их общая сумма обязана оставаться неизменной.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                KeyValueRow("Сумма A + B", Money.format(bankTotal), emphasize = true)
                KeyValueRow("Счетов участвует", bankAccounts.size.toString())
            }
        }

        if (bankAccounts.size < 2) {
            item {
                HintBox("Для опытов нужно два счёта. Нажмите любую кнопку ниже — приложение создаст " +
                    "«Учебный счёт A» и «Учебный счёт B» по 10 000 ₽ (двумя транзакциями).")
            }
        }

        items(bankAccounts, key = { it.account.id }) { entry ->
            val account = entry.account
            SectionCard(accent = AcidColors.Atomicity) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(account.type.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "операций в журнале: ${entry.operations.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MoneyText(account.balanceCents)
                }
            }
        }

        // ─────────────────────────── Простые транзакции ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Consistency) {
                SectionTitle("Одна транзакция = одно изменение + запись в журнале")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        onClick = { viewModel.bankDeposit(100_000L) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("+1 000 ₽") }
                    FilledTonalButton(
                        onClick = { viewModel.bankWithdraw(50_000L) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("−500 ₽") }
                }
                Button(
                    onClick = { viewModel.bankTransfer(50_000L) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Перевод 500 ₽ с A на B (атомарно)")
                }
                CodeBlock(
                    """
                        // ✅ одна транзакция: списание, запись, зачисление, запись
                        db.withTransaction {
                            updateBalance(from, -500_00); insertOperation(TRANSFER_OUT)
                            updateBalance(to,   +500_00); insertOperation(TRANSFER_IN)
                        }
                    """.trimIndent(),
                    AcidColors.Consistency
                )
            }
        }

        // ─────────────────────────── Плохой пример ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Danger) {
                SectionTitle("Антипример: перевод, разорванный на две транзакции")
                Text(
                    "Иногда в коде встречается «перевод» из двух независимых транзакций — " +
                        "например, списание в одной, зачисление в другой. Пока обе выполняются, " +
                        "всё выглядит нормально, но любой сбой между ними оставляет деньги " +
                        "«висящими» между счетами.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { viewModel.bankBrokenTransfer(30_000L) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Выполнить разорванный перевод 300 ₽")
                }
                CodeBlock(
                    """
                        // ❌ ОПАСНО: две транзакции на одну бизнес-операцию
                        db.withTransaction { debit(from, 300_00) }   // COMMIT — деньги ушли
                        killProcess()                                // сбой в этот момент
                        db.withTransaction { credit(to, 300_00) }    // уже никогда не выполнится
                    """.trimIndent(),
                    AcidColors.Danger
                )
            }
        }

        brokenReport?.let { report ->
            item { BrokenTransferReportCard(report, onCompensate = { viewModel.compensateBrokenTransfer() }) }
        }

        // ─────────────────────────── Стресс-тест ───────────────────────────
        item {
            SectionCard(accent = AcidColors.Isolation) {
                SectionTitle("Изоляция под нагрузкой")
                Text(
                    "20 переводов по 1 ₽ запускаются одновременно (половина «туда», половина «обратно»). " +
                        "Каждый перевод — отдельная транзакция. Инвариант: сумма по счетам не меняется, " +
                        "а в журнале остаётся ровно по две записи на каждый успешный перевод.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { viewModel.runParallelTransfers() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Запустить 20 параллельных переводов")
                }
                if (busy && stage != null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stage ?: "", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        parallelReport?.let { report ->
            item { ParallelTransferReportCard(report) }
        }

        // ─────────────────────────── Лента журнала ───────────────────────────
        item {
            SectionCard {
                SectionTitle("Что прямо сейчас происходит в базе")
                Text(
                    "Ниже — последние операции из журнала. Эти строки появляются сами: " +
                        "Room пересчитывает запрос после каждой транзакции, а Compose перерисовывает список.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                if (activity.isEmpty()) {
                    Text(
                        "Журнал пуст — выполните любую операцию выше.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                activity.take(10).forEach { entry ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${entry.accountName} · ${entry.operation.comment.ifBlank { entry.operation.type.label }}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${entry.operation.type.label} · ${formatShort(entry.operation.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Money.formatSigned(entry.operation.amountCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (entry.operation.amountCents >= 0) AcidColors.Ok else AcidColors.Danger
                        )
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Итог демонстраций")
                ResultRow(
                    ok = brokenReport == null,
                    text = if (brokenReport == null) {
                        "Разорванных переводов нет: состояние базы согласовано."
                    } else {
                        "Есть незавершённая операция: списание прошло, зачисление — нет. " +
                            "Именно поэтому в реальных платёжных системах обе половины перевода " +
                            "всегда попадают в одну транзакцию (как в WalletRepository.transfer)."
                    }
                )
                parallelReport?.let { report ->
                    ResultRow(
                        ok = report.invariantOk && report.journalIntact,
                        text = "Стресс-тест: ${report.succeeded} из ${report.requested} транзакций, " +
                            "изменение суммы по всем счетам — ${Money.formatSigned(report.totalDriftCents)}."
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Badge("WAL", AcidColors.Durability)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "После каждого COMMIT данные уже на диске — перезапуск приложения их не сотрёт.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}
