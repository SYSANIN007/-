package com.example.acidwallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AccountWithOperations
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.domain.Money

/** Поле ввода суммы с локальной валидацией. */
@Composable
private fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String?,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        supportingText = errorText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────── Создание / правка счёта ───────────────────────────

@Composable
fun AccountEditorDialog(
    initial: AccountEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, ownerName: String, type: AccountType, initialCents: Long, note: String, isActive: Boolean) -> Unit
) {
    val isNew = initial == null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var owner by remember { mutableStateOf(initial?.ownerName ?: "Студент") }
    var type by remember { mutableStateOf(initial?.type ?: AccountType.BANK_CARD) }
    var amount by remember { mutableStateOf(if (isNew) "0" else Money.formatPlain(initial!!.initialBalanceCents)) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Новый счёт (CREATE)" else "Изменить счёт (UPDATE)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Владелец") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Тип счёта", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.take(2).forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text("${candidate.emoji} ${candidate.label}") },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AccountType.entries.drop(2).forEach { candidate ->
                        FilterChip(
                            selected = type == candidate,
                            onClick = { type = candidate },
                            label = { Text("${candidate.emoji} ${candidate.label}") }
                        )
                    }
                }
                AmountField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "Начальный остаток, ₽",
                    isError = error != null,
                    errorText = error,
                    // Начальный остаток меняется только операциями журнала,
                    // поэтому при редактировании поле недоступно.
                    enabled = isNew
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Заметка") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                        Text("  Счёт активен (снимите галочку, чтобы убрать в архив)")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = Money.parseToCents(amount)
                if (cents == null) {
                    error = "Введите сумму, например 1500 или 1500,50"
                    return@Button
                }
                if (name.isBlank()) {
                    error = "У счёта должно быть название"
                    return@Button
                }
                onSave(name, owner, type, cents, note, isActive)
                onDismiss()
            }) { Text(if (isNew) "Создать" else "Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ─────────────────────────── Пополнение / списание ───────────────────────────

@Composable
fun AmountDialog(
    title: String,
    confirmLabel: String,
    defaultAmount: String = "1000",
    defaultComment: String = "",
    onDismiss: () -> Unit,
    onConfirm: (cents: Long, comment: String) -> Unit
) {
    var amount by remember { mutableStateOf(defaultAmount) }
    var comment by remember { mutableStateOf(defaultComment) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                AmountField(amount, { amount = it }, "Сумма, ₽", error != null, error)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = Money.parseToCents(amount)
                if (cents == null || cents <= 0) {
                    error = "Сумма должна быть положительным числом"
                    return@Button
                }
                onConfirm(cents, comment)
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ─────────────────────────── Перевод между счетами ───────────────────────────

@Composable
fun TransferDialog(
    accounts: List<AccountWithOperations>,
    initialFromId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (fromId: Long, toId: Long, cents: Long, comment: String) -> Unit
) {
    val active = accounts.map { it.account }.filter { it.isActive }
    var fromId by remember {
        mutableStateOf(initialFromId ?: active.firstOrNull()?.id ?: 0L)
    }
    var toId by remember {
        mutableStateOf(active.firstOrNull { it.id != fromId }?.id ?: 0L)
    }
    var amount by remember { mutableStateOf("500") }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перевод — одна транзакция") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Откуда", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.forEach { account ->
                        FilterChip(
                            selected = fromId == account.id,
                            onClick = {
                                fromId = account.id
                                if (toId == account.id) {
                                    toId = active.firstOrNull { it.id != account.id }?.id ?: toId
                                }
                            },
                            label = { Text(account.name.take(14), maxLines = 1) }
                        )
                    }
                }
                Text("Куда", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    active.filter { it.id != fromId }.forEach { account ->
                        FilterChip(
                            selected = toId == account.id,
                            onClick = { toId = account.id },
                            label = { Text(account.name.take(14), maxLines = 1) }
                        )
                    }
                }
                AmountField(amount, { amount = it }, "Сумма, ₽", error != null, error)
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Обе половины перевода (списание и зачисление) попадут в одну транзакцию: " +
                        "если не хватит денег — не изменится ничего.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = Money.parseToCents(amount)
                if (cents == null || cents <= 0) {
                    error = "Сумма должна быть положительным числом"
                    return@Button
                }
                if (fromId == toId || fromId == 0L || toId == 0L) {
                    error = "Выберите два разных счёта"
                    return@Button
                }
                onConfirm(fromId, toId, cents, comment)
                onDismiss()
            }) { Text("Перевести") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ─────────────────────────── Правка операции журнала ───────────────────────────

@Composable
fun OperationEditorDialog(
    operation: OperationEntity,
    accountName: String,
    onDismiss: () -> Unit,
    onSave: (cents: Long, comment: String) -> Unit
) {
    var amount by remember { mutableStateOf(Money.formatPlain(kotlin.math.abs(operation.amountCents))) }
    var comment by remember { mutableStateOf(operation.comment) }
    var error by remember { mutableStateOf<String?>(null) }
    val isIncome = operation.type.isIncome || operation.amountCents >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить операцию (UPDATE)") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Счёт: $accountName · ${operation.type.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AmountField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = if (isIncome) "Сумма прихода, ₽" else "Сумма расхода, ₽",
                    isError = error != null,
                    errorText = error
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Комментарий") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Баланс счёта будет пересчитан на разницу сумм — тоже внутри транзакции.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = Money.parseToCents(amount)
                if (cents == null || cents <= 0) {
                    error = "Сумма должна быть положительным числом"
                    return@Button
                }
                onSave(if (isIncome) cents else -cents, comment)
                onDismiss()
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ─────────────────────────── Подтверждение ───────────────────────────

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
