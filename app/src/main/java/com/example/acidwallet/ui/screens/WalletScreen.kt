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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AccountWithOperations
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.WalletViewModel
import com.example.acidwallet.ui.components.AccountEditorDialog
import com.example.acidwallet.ui.components.AmountDialog
import com.example.acidwallet.ui.components.Badge
import com.example.acidwallet.ui.components.ConfirmDialog
import com.example.acidwallet.ui.components.HintBox
import com.example.acidwallet.ui.components.KeyValueRow
import com.example.acidwallet.ui.components.MoneyText
import com.example.acidwallet.ui.components.OperationEditorDialog
import com.example.acidwallet.ui.components.ResultRow
import com.example.acidwallet.ui.components.SectionTitle
import com.example.acidwallet.ui.components.SectionCard
import com.example.acidwallet.ui.components.TransferDialog
import com.example.acidwallet.ui.formatShort
import com.example.acidwallet.ui.theme.AcidColors

/**
 * Главный экран: полный CRUD над счетами и журналом операций.
 *
 * Список счетов приходит из Room как Flow, поэтому UI обновляется сам —
 * после любой транзакции (в том числе запущенной из другой вкладки)
 * карточки и журнал перерисовываются без ручного обновления.
 */
@Composable
fun WalletScreen(viewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val total by viewModel.totalBalance.collectAsStateWithLifecycle()
    val journalRows by viewModel.totalJournalRows.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDeposit by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToWithdraw by remember { mutableStateOf<AccountEntity?>(null) }
    var accountToDelete by remember { mutableStateOf<AccountEntity?>(null) }
    var transferFromId by remember { mutableStateOf<Long?>(null) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var operationToEdit by remember { mutableStateOf<OperationEntity?>(null) }
    var operationToCancel by remember { mutableStateOf<OperationEntity?>(null) }
    var operationToDelete by remember { mutableStateOf<OperationEntity?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(accent = AcidColors.Consistency) {
                SectionTitle("Итого по счетам")
                MoneyText(total)
                KeyValueRow("Счетов", accounts.size.toString())
                KeyValueRow("Строк в журнале операций", journalRows.toString())
                Text(
                    "Баланс каждого счёта и его журнал меняются в одной транзакции, " +
                        "поэтому инвариант «баланс = начальный остаток + Σ операций» всегда сходится.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { viewModel.recalculate() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Сверка журнала")
                    }
                    OutlinedButton(onClick = { showResetConfirm = true }) {
                        Text("Сбросить демо-данные")
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("CRUD в этом приложении")
                KeyValueRow("CREATE", "«Новый счёт» — INSERT в accounts + системная запись журнала")
                KeyValueRow("READ", "Карточки счетов и журнал: @Query + @Relation, всё на Flow")
                KeyValueRow("UPDATE", "Правка счёта, правка операции, отмена операции, сверка журнала")
                KeyValueRow("DELETE", "Удаление операции (с корректировкой баланса) и счёта (CASCADE)")
            }
        }

        item {
            Button(onClick = { showCreateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Новый счёт (CREATE)")
            }
        }

        if (accounts.isEmpty()) {
            item { HintBox("Счетов пока нет. Нажмите «Новый счёт» — или сбросьте демо-данные выше.") }
        }

        items(accounts, key = { it.account.id }) { entry ->
            AccountCard(
                entry = entry,
                onDeposit = { accountToDeposit = it },
                onWithdraw = { accountToWithdraw = it },
                onTransfer = {
                    transferFromId = it.id
                    showTransferDialog = true
                },
                onEdit = { accountToEdit = it },
                onDelete = { accountToDelete = it },
                onEditOperation = { operationToEdit = it },
                onCancelOperation = { operationToCancel = it },
                onDeleteOperation = { operationToDelete = it }
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    // ─────────────────────────────── Диалоги ───────────────────────────────

    if (showCreateDialog) {
        AccountEditorDialog(
            initial = null,
            onDismiss = { showCreateDialog = false },
            onSave = { name, owner, type, cents, note, _ ->
                viewModel.createAccount(name, owner, type, cents, note)
            }
        )
    }

    accountToEdit?.let { account ->
        AccountEditorDialog(
            initial = account,
            onDismiss = { accountToEdit = null },
            onSave = { name, owner, type, cents, note, isActive ->
                viewModel.updateAccount(account.id, name, owner, type, note, isActive)
                if (cents != account.initialBalanceCents) {
                    viewModel.notify("Начальный остаток меняется только через операции журнала")
                }
            }
        )
    }

    accountToDeposit?.let { account ->
        AmountDialog(
            title = "Пополнить «${account.name}»",
            confirmLabel = "Пополнить",
            defaultComment = "Пополнение",
            onDismiss = { accountToDeposit = null },
            onConfirm = { cents, comment -> viewModel.deposit(account.id, cents, comment) }
        )
    }

    accountToWithdraw?.let { account ->
        AmountDialog(
            title = "Списать с «${account.name}»",
            confirmLabel = "Списать",
            defaultAmount = "500",
            defaultComment = "Списание",
            onDismiss = { accountToWithdraw = null },
            onConfirm = { cents, comment -> viewModel.withdraw(account.id, cents, comment) }
        )
    }

    if (showTransferDialog) {
        TransferDialog(
            accounts = accounts,
            initialFromId = transferFromId,
            onDismiss = { showTransferDialog = false },
            onConfirm = { fromId, toId, cents, comment -> viewModel.transfer(fromId, toId, cents, comment) }
        )
    }

    accountToDelete?.let { account ->
        ConfirmDialog(
            title = "Удалить счёт?",
            text = "Счёт «${account.name}» и все его операции будут удалены. " +
                "Журнал уходит каскадом (ON DELETE CASCADE) — «осиротевших» строк в базе не останется.",
            confirmLabel = "Удалить",
            onDismiss = { accountToDelete = null },
            onConfirm = { viewModel.deleteAccount(account) }
        )
    }

    operationToEdit?.let { operation ->
        val accountName = accounts.firstOrNull { it.account.id == operation.accountId }?.account?.name ?: "—"
        OperationEditorDialog(
            operation = operation,
            accountName = accountName,
            onDismiss = { operationToEdit = null },
            onSave = { cents, comment -> viewModel.updateOperation(operation.id, cents, comment) }
        )
    }

    operationToCancel?.let { operation ->
        ConfirmDialog(
            title = "Отменить операцию?",
            text = "Запись останется в журнале со статусом «Отменена» (это аудит-лог), " +
                "а баланс вернётся к прежнему значению — всё внутри одной транзакции.",
            confirmLabel = "Отменить операцию",
            onDismiss = { operationToCancel = null },
            onConfirm = { viewModel.cancelOperation(operation.id) }
        )
    }

    operationToDelete?.let { operation ->
        ConfirmDialog(
            title = "Удалить операцию?",
            text = "Строка журнала будет удалена, а баланс счёта скорректирован на её сумму " +
                "(одна транзакция).",
            confirmLabel = "Удалить",
            onDismiss = { operationToDelete = null },
            onConfirm = { viewModel.deleteOperation(operation.id) }
        )
    }

    if (showResetConfirm) {
        ConfirmDialog(
            title = "Сбросить данные?",
            text = "Все счета и операции будут удалены, затем база снова наполнится демонстрационными данными. " +
                "Действие выполняется одной транзакцией.",
            confirmLabel = "Сбросить",
            onDismiss = { showResetConfirm = false },
            onConfirm = { viewModel.resetDemoData() }
        )
    }
}

/** Карточка счёта: данные, действия и журнал операций. */
@Composable
private fun AccountCard(
    entry: AccountWithOperations,
    onDeposit: (AccountEntity) -> Unit,
    onWithdraw: (AccountEntity) -> Unit,
    onTransfer: (AccountEntity) -> Unit,
    onEdit: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit,
    onEditOperation: (OperationEntity) -> Unit,
    onCancelOperation: (OperationEntity) -> Unit,
    onDeleteOperation: (OperationEntity) -> Unit
) {
    val account = entry.account
    val activeSum = entry.operations
        .filter { it.status != OperationStatus.CANCELLED }
        .sumOf { it.amountCents }
    val invariantOk = account.balanceCents == account.initialBalanceCents + activeSum
    val accent = if (account.isActive) AcidColors.Atomicity else MaterialTheme.colorScheme.outline

    SectionCard(accent = accent) {
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
                    "${account.type.label} · ${account.ownerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(account.balanceCents)
                if (!account.isActive) Badge("архив", MaterialTheme.colorScheme.outline)
            }
        }

        if (account.note.isNotBlank()) {
            Text(
                account.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        KeyValueRow("Начальный остаток", Money.format(account.initialBalanceCents))
        KeyValueRow("Σ операций журнала", Money.format(activeSum))
        ResultRow(
            ok = invariantOk,
            text = if (invariantOk) {
                "Инвариант сходится: ${Money.format(account.initialBalanceCents)} + ${Money.format(activeSum)} = " +
                    "${Money.format(account.balanceCents)} (CONSISTENCY)"
            } else {
                "Инвариант нарушен! Баланс ${Money.format(account.balanceCents)}, а по журналу " +
                    "должно быть ${Money.format(account.initialBalanceCents + activeSum)}. " +
                    "Нажмите «Сверка журнала» — она починит данные одной транзакцией."
            }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onDeposit(account) }, modifier = Modifier.weight(1f)) {
                Text("Пополнить")
            }
            FilledTonalButton(onClick = { onWithdraw(account) }, modifier = Modifier.weight(1f)) {
                Text("Списать")
            }
            FilledTonalButton(onClick = { onTransfer(account) }, modifier = Modifier.weight(1f)) {
                Text("Перевод")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onEdit(account) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Изменить")
            }
            OutlinedButton(onClick = { onDelete(account) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Удалить")
            }
        }

        HorizontalDivider()
        SectionTitle("Журнал операций · ${entry.operations.size}")
        if (entry.operations.isEmpty()) {
            Text(
                "Операций пока нет",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        entry.operations.sortedByDescending { it.createdAt }.forEach { operation ->
            OperationRow(
                operation = operation,
                onEdit = { onEditOperation(operation) },
                onCancel = { onCancelOperation(operation) },
                onDelete = { onDeleteOperation(operation) }
            )
        }
    }
}

/** Одна строка журнала с действиями UPDATE/DELETE. */
@Composable
private fun OperationRow(
    operation: OperationEntity,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val isCancelled = operation.status == OperationStatus.CANCELLED
    val amountColor = when {
        isCancelled -> MaterialTheme.colorScheme.onSurfaceVariant
        operation.amountCents >= 0 -> AcidColors.Ok
        else -> AcidColors.Danger
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Money.formatSigned(operation.amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = amountColor,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(8.dp))
                if (isCancelled) {
                    Badge("отменена", AcidColors.Danger)
                } else {
                    Badge(operation.type.label, if (operation.amountCents >= 0) AcidColors.Ok else AcidColors.Atomicity)
                }
            }
            Text(
                operation.comment.ifBlank { operation.type.label },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                formatShort(operation.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val editable = operation.type != OperationType.SYSTEM
        if (editable) {
            if (!isCancelled) {
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                    Text("↺", color = AcidColors.Durability, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Изменить операцию", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить операцию", modifier = Modifier.size(18.dp))
            }
        } else {
            Badge("системная", MaterialTheme.colorScheme.outline)
        }
    }
}
