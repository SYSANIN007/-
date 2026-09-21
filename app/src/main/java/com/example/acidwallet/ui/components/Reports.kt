package com.example.acidwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.acidwallet.demo.AtomicityReport
import com.example.acidwallet.demo.AtomicityStep
import com.example.acidwallet.demo.BrokenTransferReport
import com.example.acidwallet.demo.IsolationCase
import com.example.acidwallet.demo.IsolationReport
import com.example.acidwallet.demo.ParallelTransferReport
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.theme.AcidColors

/** Протокол эксперимента с атомарностью: три шага и их измерения. */
@Composable
fun AtomicityReportCard(report: AtomicityReport) {
    SectionCard(accent = AcidColors.Atomicity) {
        Text("Протокол эксперимента · Atomicity", style = MaterialTheme.typography.titleMedium)
        Text(
            "Лабораторный счёт: «${report.accountName}» (id ${report.accountId}). " +
                "Стартовый баланс ${Money.format(report.startBalanceCents)}, " +
                "строк журнала до опыта: ${report.journalRowsBefore}, " +
                "после всех шагов: ${report.journalRowsAfter}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        AtomicityStepBlock(report.commitStep, AcidColors.Atomicity)
        AtomicityStepBlock(report.dangerousStep, AcidColors.Danger)
        AtomicityStepBlock(report.rollbackStep, AcidColors.Consistency)
        HorizontalDivider()
        ResultRow(
            ok = report.consistent,
            text = if (report.consistent) {
                "Итог: транзакции работают — либо применяется всё, либо ничего. " +
                    "Служебный счёт лаборатории после опыта восстановлен."
            } else {
                "Итог: обнаружено расхождение — смотрите значения выше."
            }
        )
    }
}

@Composable
private fun AtomicityStepBlock(step: AtomicityStep, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusDot(color)
            Text(
                "  ${step.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            step.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KeyValueRow("Строк журнала записано / планировалось", "${step.committedRows} / ${step.attemptedRows}")
        KeyValueRow(
            "Изменение баланса",
            "${Money.formatSigned(step.actualBalanceDeltaCents)} (планировалось ${Money.formatSigned(step.attemptedBalanceDeltaCents)})"
        )
        KeyValueRow(
            "Расхождение «баланс ↔ журнал»",
            Money.format(step.invariantMismatchCents),
            valueColor = if (step.invariantHolds) AcidColors.Ok else AcidColors.Danger,
            emphasize = true
        )
        step.errorMessage?.let {
            Text(
                "Исключение: $it",
                style = MaterialTheme.typography.bodySmall,
                color = AcidColors.Danger
            )
        }
        ResultRow(
            ok = step.isAtomic && step.invariantHolds,
            text = if (step.isAtomic && step.invariantHolds) {
                "Атомарность соблюдена, инвариант цел."
            } else {
                "Изменения применились частично — инвариант нарушен. Так делать нельзя."
            }
        )
        CodeBlock(step.code, color)
        HorizontalDivider()
    }
}

/** Протокол эксперимента с изоляцией: четыре опыта. */
@Composable
fun IsolationReportCard(report: IsolationReport, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier, accent = AcidColors.Isolation) {
        Text("Протокол экспериментов · Isolation", style = MaterialTheme.typography.titleMedium)
        Text(
            "Служебные счета: «${report.accountAName}» и «${report.accountBName}». " +
                "Стартовая сумма A + B = ${Money.format(report.startSumCents)}. " +
                "Деньги в опытах только перекладываются внутри пары, поэтому сумма — инвариант.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        report.cases.forEach { case ->
            IsolationCaseBlock(case)
        }
        HorizontalDivider()
        KeyValueRow(
            "Суммарный «увод» денег от ожидаемого",
            Money.formatSigned(report.totalDriftCents),
            valueColor = if (report.invariantRestored) AcidColors.Ok else AcidColors.Danger,
            emphasize = true
        )
        ResultRow(
            ok = report.invariantRestored,
            text = if (report.invariantRestored) {
                "После восстановления демо-счетов сумма снова сходится: ${Money.format(report.startSumCents)}."
            } else {
                "Сходимость пришлось восстанавливать компенсирующими операциями — " +
                    "ровно так в реальной жизни «чинят» данные после гонок."
            }
        )
    }
}

@Composable
private fun IsolationCaseBlock(case: IsolationCase) {
    val color = if (case.ok) AcidColors.Consistency else AcidColors.Danger
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusDot(color)
            Text(
                "  Опыт ${case.index}. ${case.title}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        MonoText("режим: ${case.mode}")
        KeyValueRow("Ожидаемое изменение суммы", Money.formatSigned(case.expectedDeltaCents))
        KeyValueRow(
            "Фактическое изменение суммы",
            Money.formatSigned(case.observedDeltaCents),
            valueColor = if (case.ok) AcidColors.Ok else AcidColors.Danger,
            emphasize = true
        )
        if (case.totalReads > 0) {
            KeyValueRow(
                "Отчётов прочитано / из них противоречивых",
                "${case.totalReads} / ${case.inconsistentReads}",
                valueColor = if (case.inconsistentReads == 0) AcidColors.Ok else AcidColors.Danger
            )
        }
        KeyValueRow("Операций записи / ожиданий очереди", "${case.transactions} / ${case.conflicts}")
        KeyValueRow("Время выполнения", "${case.durationMs} мс")
        Text(
            case.conclusion,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CodeBlock(case.code, color)
        HorizontalDivider()
    }
}

/** Отчёт «разорванного» перевода. */
@Composable
fun BrokenTransferReportCard(report: BrokenTransferReport, onCompensate: () -> Unit) {
    SectionCard(accent = AcidColors.Danger) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusDot(AcidColors.Danger)
            Text("  ⚠ Перевод, разорванный на две транзакции", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Одна бизнес-операция была разбита на две независимые транзакции. " +
                "После первой (списание) процесс «упал», поэтому вторая (зачисление) не выполнилась.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KeyValueRow(
            "«${report.fromName}»",
            "${Money.format(report.fromBeforeCents)} → ${Money.format(report.fromAfterCents)}"
        )
        KeyValueRow(
            "«${report.toName}»",
            "${Money.format(report.toBeforeCents)} → ${Money.format(report.toAfterCents)}"
        )
        KeyValueRow("Списание применилось", if (report.debitApplied) "да" else "нет")
        KeyValueRow("Зачисление применилось", if (report.creditApplied) "да" else "нет")
        KeyValueRow(
            "Сумма по всем счетам изменилась на",
            Money.formatSigned(report.totalDriftCents),
            valueColor = AcidColors.Danger,
            emphasize = true
        )
        Text(
            "Деньги «зависли» между счетами: ${Money.format(report.amountCents)} списались, " +
                "но никому не зачислились. Приложение теперь обязано провести компенсирующую " +
                "операцию — и хорошо, если у вас есть журнал, по которому можно понять, что именно сломалось.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.material3.FilledTonalButton(onClick = onCompensate, modifier = Modifier.fillMaxWidth()) {
            Text("Провести компенсацию (+${Money.format(report.amountCents)})")
        }
        CodeBlock(
            """
                // ❌ ОПАСНО: две транзакции на одну бизнес-операцию
                db.withTransaction { debit(from, amount) }    // ← COMMIT состоялся
                crashOrRestart()                              // ← сбой
                db.withTransaction { credit(to, amount) }     // ← не выполнилось

                // ✅ ПРАВИЛЬНО: обе половины в одной транзакции (см. WalletRepository.transfer)
            """.trimIndent(),
            AcidColors.Danger
        )
    }
}

/** Отчёт стресс-теста с параллельными транзакциями. */
@Composable
fun ParallelTransferReportCard(report: ParallelTransferReport) {
    SectionCard(accent = if (report.invariantOk) AcidColors.Consistency else AcidColors.Danger) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            StatusDot(if (report.invariantOk) AcidColors.Ok else AcidColors.Danger)
            Text("  Стресс-тест: ${report.requested} параллельных транзакций", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Каждый перевод выполнялся в собственной транзакции и все они стартовали " +
                "одновременно: половина «туда», половина «обратно».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KeyValueRow("Успешных транзакций", "${report.succeeded}")
        KeyValueRow("Отказов (после 3 попыток)", "${report.failed}")
        KeyValueRow(
            "Записей журнала добавлено / ожидалось",
            "${report.journalRowsAdded} / ${report.expectedJournalRows}",
            valueColor = if (report.journalIntact) AcidColors.Ok else AcidColors.Danger
        )
        KeyValueRow(
            "«${report.fromName}»",
            "${Money.format(report.fromBeforeCents)} → ${Money.format(report.fromAfterCents)}"
        )
        KeyValueRow(
            "«${report.toName}»",
            "${Money.format(report.toBeforeCents)} → ${Money.format(report.toAfterCents)}"
        )
        KeyValueRow(
            "Сумма по всем счетам изменилась на",
            Money.formatSigned(report.totalDriftCents),
            valueColor = if (report.invariantOk) AcidColors.Ok else AcidColors.Danger,
            emphasize = true
        )
        KeyValueRow("Время выполнения", "${report.durationMs} мс")
        ResultRow(
            ok = report.invariantOk && report.journalIntact,
            text = if (report.invariantOk && report.journalIntact) {
                "Инвариант сохранён: ${report.succeeded} транзакций прошли параллельно, " +
                    "но ни одна копейка не потерялась и ни одна половинка перевода не пропала из журнала."
            } else {
                "Обнаружено расхождение — проверьте журнал и балансы."
            }
        )
    }
}
