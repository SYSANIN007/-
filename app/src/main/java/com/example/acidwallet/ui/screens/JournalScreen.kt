package com.example.acidwallet.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.domain.Money
import com.example.acidwallet.ui.WalletViewModel
import com.example.acidwallet.ui.components.Badge
import com.example.acidwallet.ui.components.ConfirmDialog
import com.example.acidwallet.ui.components.HintBox
import com.example.acidwallet.ui.components.OperationEditorDialog
import com.example.acidwallet.ui.components.SectionCard
import com.example.acidwallet.ui.components.SectionTitle
import com.example.acidwallet.ui.formatDateTime
import com.example.acidwallet.ui.theme.AcidColors

/**
 * Журнал всех операций: это второй «READ» и одновременно аудит-лог.
 *
 * Фильтры не фильтруют список в памяти — они подставляются в SQL-запрос Room
 * (`observeOperationsFiltered`), поэтому работает и поиск по подстроке,
 * и фильтр по типу/статусу/счёту, и всё это реактивно.
 */
@Composable
fun JournalScreen(viewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val filters by viewModel.journalFilters.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()

    var operationToEdit by remember { mutableStateOf<OperationEntity?>(null) }
    var operationToCancel by remember { mutableStateOf<OperationEntity?>(null) }
    var operationToDelete by remember { mutableStateOf<OperationEntity?>(null) }

    val accountNames = accounts.associate { it.account.id to it.account.name }
    val filteredSum = journal.filter { it.status != OperationStatus.CANCELLED }.sumOf { it.amountCents }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                SectionTitle("Поиск и фильтры")
                OutlinedTextField(
                    value = filters.query,
                    onValueChange = { viewModel.setJournalQuery(it) },
                    label = { Text("Поиск по комментарию") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Счёт", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filters.accountId == null,
                        onClick = { viewModel.setJournalAccount(null) },
                        label = { Text("все") }
                    )
                    accounts.forEach { entry ->
                        FilterChip(
                            selected = filters.accountId == entry.account.id,
                            onClick = { viewModel.setJournalAccount(entry.account.id) },
                            label = { Text(entry.account.name.take(16), maxLines = 1) }
                        )
                    }
                }
                Text("Тип операции", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filters.type == null,
                        onClick = { viewModel.setJournalType(null) },
                        label = { Text("все") }
                    )
                    OperationType.entries.forEach { type ->
                        FilterChip(
                            selected = filters.type == type,
                            onClick = { viewModel.setJournalType(type) },
                            label = { Text(type.label) }
                        )
                    }
                }
                Text("Статус", style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filters.status == null,
                        onClick = { viewModel.setJournalStatus(null) },
                        label = { Text("любой") }
                    )
                    OperationStatus.entries.forEach { status ->
                        FilterChip(
                            selected = filters.status == status,
                            onClick = { viewModel.setJournalStatus(status) },
                            label = { Text(status.label) }
                        )
                    }
                }
            }
        }

        item {
            SectionCard(accent = AcidColors.Isolation) {
                SectionTitle("Результат выборки")
                Text(
                    "Найдено операций: ${journal.size} · сумма проведённых: ${Money.format(filteredSum)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Каждая строка — результат SQL-запроса с параметрами " +
                        "(WHERE :accountId IS NULL OR …). Изменения в базе приходят сюда сами: " +
                        "Room следит за таблицами accounts и operations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (journal.isEmpty()) {
            item { HintBox("Ничего не найдено. Измените фильтры или очистите поиск.") }
        }

        items(journal, key = { it.id }) { operation ->
            JournalEntryCard(
                operation = operation,
                accountName = accountNames[operation.accountId] ?: "Счёт #${operation.accountId}",
                onEdit = { operationToEdit = operation },
                onCancel = { operationToCancel = operation },
                onDelete = { operationToDelete = operation }
            )
        }

        item { Spacer(Modifier.height(40.dp)) }
    }

    operationToEdit?.let { operation ->
        OperationEditorDialog(
            operation = operation,
            accountName = accountNames[operation.accountId] ?: "—",
            onDismiss = { operationToEdit = null },
            onSave = { cents, comment -> viewModel.updateOperation(operation.id, cents, comment) }
        )
    }

    operationToCancel?.let { operation ->
        ConfirmDialog(
            title = "Отменить операцию?",
            text = "Статус станет «Отменена», баланс счёта вернётся к прежнему значению. " +
                "Запись остаётся в журнале — это история (аудит-лог).",
            confirmLabel = "Отменить",
            onDismiss = { operationToCancel = null },
            onConfirm = { viewModel.cancelOperation(operation.id) }
        )
    }

    operationToDelete?.let { operation ->
        ConfirmDialog(
            title = "Удалить операцию?",
            text = "Строка будет удалена из журнала, баланс счёта скорректирован на её сумму. " +
                "В отличие от отмены, следов в журнале не останется.",
            confirmLabel = "Удалить",
            onDismiss = { operationToDelete = null },
            onConfirm = { viewModel.deleteOperation(operation.id) }
        )
    }
}

@Composable
private fun JournalEntryCard(
    operation: OperationEntity,
    accountName: String,
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
    val isSystem = operation.type == OperationType.SYSTEM

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    accountName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    operation.comment.ifBlank { operation.type.label },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${operation.type.label} · ${formatDateTime(operation.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.formatSigned(operation.amountCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
                if (isCancelled) Badge("отменена", AcidColors.Danger)
                else if (isSystem) Badge("системная", MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isSystem) {
                OutlinedButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Изменить")
                }
                if (!isCancelled) {
                    OutlinedButton(onClick = onCancel) { Text("Отменить") }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                }
            } else {
                Text(
                    "Системная запись: создаётся приложением, менять её нельзя.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
