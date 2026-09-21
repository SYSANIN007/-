package com.example.acidwallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.acidwallet.data.DemoSeeder
import com.example.acidwallet.data.RecalculationReport
import com.example.acidwallet.data.WalletRepository
import com.example.acidwallet.data.local.AccountEntity
import com.example.acidwallet.data.local.AccountWithOperations
import com.example.acidwallet.data.local.AppDatabase
import com.example.acidwallet.data.local.OperationEntity
import com.example.acidwallet.data.local.OperationWithAccount
import com.example.acidwallet.data.model.AccountType
import com.example.acidwallet.data.model.OperationStatus
import com.example.acidwallet.data.model.OperationType
import com.example.acidwallet.demo.AtomicityLab
import com.example.acidwallet.demo.AtomicityReport
import com.example.acidwallet.demo.BrokenTransferReport
import com.example.acidwallet.demo.IsolationLab
import com.example.acidwallet.demo.IsolationReport
import com.example.acidwallet.demo.ParallelTransferReport
import com.example.acidwallet.domain.Money
import com.example.acidwallet.domain.WalletException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Текущие фильтры журнала операций. */
data class JournalFilters(
    val query: String = "",
    val accountId: Long? = null,
    val type: OperationType? = null,
    val status: OperationStatus? = null
)

/**
 * Единственный ViewModel приложения: он держит состояние экрана и вызывает
 * репозиторий. Вся работа с базой идёт в [viewModelScope] — корутины
 * автоматически отменяются при уничтожении Activity, поэтому «висящих»
 * транзакций после закрытия экрана не остаётся.
 */
class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val repo = WalletRepository(db)
    private val seeder = DemoSeeder(db)
    private val atomicityLab = AtomicityLab(db)

    // ───────────────────────────── Состояние ─────────────────────────────

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Подпись текущего шага длинной операции (например, ACID-эксперимента). */
    private val _stage = MutableStateFlow<String?>(null)
    val stage: StateFlow<String?> = _stage.asStateFlow()

    private val _atomicityReport = MutableStateFlow<AtomicityReport?>(null)
    val atomicityReport: StateFlow<AtomicityReport?> = _atomicityReport.asStateFlow()

    private val _isolationReport = MutableStateFlow<IsolationReport?>(null)
    val isolationReport: StateFlow<IsolationReport?> = _isolationReport.asStateFlow()

    private val _brokenReport = MutableStateFlow<BrokenTransferReport?>(null)
    val brokenReport: StateFlow<BrokenTransferReport?> = _brokenReport.asStateFlow()

    private val _parallelReport = MutableStateFlow<ParallelTransferReport?>(null)
    val parallelReport: StateFlow<ParallelTransferReport?> = _parallelReport.asStateFlow()

    private val _durabilityMarks = MutableStateFlow(0)
    val durabilityMarks: StateFlow<Int> = _durabilityMarks.asStateFlow()

    /** Результат последней сверки журнала (проверка инвариантов, свойство C). */
    private val _lastRecalc = MutableStateFlow<RecalculationReport?>(null)
    val lastRecalc: StateFlow<RecalculationReport?> = _lastRecalc.asStateFlow()

    private val _journalMode = MutableStateFlow("—")
    val journalMode: StateFlow<String> = _journalMode.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** READ: реактивный список счетов с журналами (обновляется сам после CRUD). */
    val accounts: StateFlow<List<AccountWithOperations>> = repo.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalBalance: StateFlow<Long> = repo.observeTotalBalance()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** READ: общая лента операций (JOIN двух таблиц). */
    val activity: StateFlow<List<OperationWithAccount>> = repo.observeActivity(40)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalJournalRows: StateFlow<Int> = db.isolationDao().observeTotalOperationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Первые два счёта — «учебный банк» для живых демонстраций ACID. */
    val bankAccounts: StateFlow<List<AccountWithOperations>> = accounts
        .map { it.take(2) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ───────────────────── READ журнала с фильтрами ─────────────────────

    private val filterQuery = MutableStateFlow("")
    private val filterAccountId = MutableStateFlow<Long?>(null)
    private val filterType = MutableStateFlow<OperationType?>(null)
    private val filterStatus = MutableStateFlow<OperationStatus?>(null)

    val journalFilters: StateFlow<JournalFilters> =
        combine(filterQuery, filterAccountId, filterType, filterStatus) { query, account, type, status ->
            JournalFilters(query, account, type, status)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalFilters())

    /**
     * Журнал операций. Параметры подставляются в SQL-запрос Room напрямую
     * (`WHERE :type IS NULL OR type = :type`), поэтому фильтрация происходит
     * внутри SQLite, а не в памяти телефона.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val journal: StateFlow<List<OperationEntity>> =
        journalFilters.flatMapLatest { filters ->
            db.walletDao().observeOperationsFiltered(
                accountId = filters.accountId,
                query = filters.query,
                typeName = filters.type?.name,
                statusName = filters.status?.name,
                limit = 200
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setJournalQuery(value: String) {
        filterQuery.value = value
    }

    fun setJournalAccount(accountId: Long?) {
        filterAccountId.value = accountId
    }

    fun setJournalType(type: OperationType?) {
        filterType.value = type
    }

    fun setJournalStatus(status: OperationStatus?) {
        filterStatus.value = status
    }

    init {
        viewModelScope.launch {
            try {
                repo.seedIfEmpty()
                _journalMode.value = repo.journalMode()
                _durabilityMarks.value = atomicityLab.durabilityMarks()
            } catch (t: Throwable) {
                notify("Не удалось подготовить базу: ${t.message}")
            }
        }
    }

    // ───────────────────────────── CRUD ─────────────────────────────

    /** CREATE счёта. */
    fun createAccount(name: String, ownerName: String, type: AccountType, initialCents: Long, note: String) =
        run("Счёт «${name.ifBlank { "Без названия" }}» создан ✔") {
            repo.createAccount(name, ownerName, type, initialCents, note)
        }

    /** UPDATE «паспортных» данных счёта (в том числе архивация). */
    fun updateAccount(
        accountId: Long,
        name: String,
        ownerName: String,
        type: AccountType,
        note: String,
        isActive: Boolean
    ) = run("Изменения сохранены ✔") {
        repo.updateAccount(accountId, name, ownerName, type, note, isActive)
    }

    /** DELETE счёта вместе с журналом (ON DELETE CASCADE). */
    fun deleteAccount(account: AccountEntity) = run("Счёт «${account.name}» удалён вместе с журналом") {
        repo.deleteAccount(account.id)
    }

    /** T: пополнение счёта. */
    fun deposit(accountId: Long, cents: Long, comment: String) = run("Зачислено ${Money.format(cents)} ✔") {
        repo.deposit(accountId, cents, comment)
    }

    /** T: списание со счёта. */
    fun withdraw(accountId: Long, cents: Long, comment: String) = run("Списано ${Money.format(cents)} ✔") {
        repo.withdraw(accountId, cents, comment)
    }

    /** T: перевод между счетами — две записи журнала в одной транзакции. */
    fun transfer(fromAccountId: Long, toAccountId: Long, cents: Long, comment: String) =
        run("Перевод ${Money.format(cents)} выполнен атомарно ✔") {
            repo.transfer(fromAccountId, toAccountId, cents, comment)
        }

    /** UPDATE операции журнала с корректировкой баланса. */
    fun updateOperation(operationId: Long, cents: Long, comment: String) = run("Операция изменена ✔") {
        repo.updateOperation(operationId, cents, comment)
    }

    /** UPDATE статуса: отмена операции (запись остаётся в журнале как аудит-лог). */
    fun cancelOperation(operationId: Long) = run("Операция отменена, баланс восстановлен ✔") {
        repo.cancelOperation(operationId)
    }

    /** DELETE операции журнала с корректировкой баланса. */
    fun deleteOperation(operationId: Long) = run("Операция удалена ✔") {
        repo.deleteOperation(operationId)
    }

    /** UPDATE: сверка журнала — пересчёт балансов по инварианту. */
    fun recalculate() = run(null) {
        val report = repo.recalculateBalances()
        _lastRecalc.value = report
        notify(
            if (report.isConsistent) {
                "Сверка: проверено счетов — ${report.checkedAccounts}. Расхождений нет ✔ " +
                    "(баланс = начальный остаток + Σ журнала)"
            } else {
                "Сверка: восстановлено счетов — ${report.fixedAccounts.size} / ${report.checkedAccounts}. " +
                    report.fixedAccounts.joinToString()
            }
        )
    }

    /** Полный сброс базы к начальному демонстрационному состоянию. */
    fun resetDemoData() = run(null) {
        seeder.resetEverything()
        _atomicityReport.value = null
        _isolationReport.value = null
        _brokenReport.value = null
        _parallelReport.value = null
        _journalMode.value = repo.journalMode()
        _durabilityMarks.value = atomicityLab.durabilityMarks()
        notify("База сброшена к демонстрационным данным ✔")
    }

    // ───────────────────── ACID: лаборатория атомарности ─────────────────────

    /** Три шага: COMMIT, «полуприменённое» состояние без транзакции и ROLLBACK. */
    fun runAtomicityDemo() = runLong {
        _atomicityReport.value = atomicityLab.run { message -> _stage.value = message }
    }

    /** Оставляет отметку в журнале — проверка долговечности после перезапуска. */
    fun leaveDurabilityMark() = run(null) {
        val marks = atomicityLab.leaveDurabilityMark()
        _durabilityMarks.value = marks
        notify("Отметка №$marks записана. Полностью закройте приложение и откройте снова — она останется на месте ✔")
    }

    // ───────────────────── ACID: лаборатория изоляции ─────────────────────

    /** Четыре эксперимента с изоляцией на служебных счетах A и B. */
    fun runIsolationDemo() = runLong {
        val demo = repo.ensureDemoAccounts()
        val a = demo.firstOrNull { it.name == DemoSeeder.DEMO_A_NAME }
        val b = demo.firstOrNull { it.name == DemoSeeder.DEMO_B_NAME }
        requireNotNull(a) { "Не удалось создать демонстрационный счёт A" }
        requireNotNull(b) { "Не удалось создать демонстрационный счёт B" }

        val lab = IsolationLab(
            db = db,
            io = viewModelScope,
            demoAId = a.id,
            demoBId = b.id,
            demoAName = a.name,
            demoBName = b.name
        )
        _isolationReport.value = lab.run(DemoSeeder.DEMO_START_CENTS) { message -> _stage.value = message }
        _stage.value = null
    }

    // ───────────────────── Живые демонстрации на своих счетах ─────────────────────

    /** Простая транзакция: пополнение «учебного» счёта. */
    fun bankDeposit(cents: Long) = run(null) {
        val (a, _) = prepareBank()
        repo.deposit(a.id, cents, "Живая демонстрация: пополнение")
        notify("Выполнена одна атомарная транзакция: баланс + журнал ✔")
    }

    /** Простая транзакция: списание. */
    fun bankWithdraw(cents: Long) = run(null) {
        val (a, _) = prepareBank()
        repo.withdraw(a.id, cents, "Живая демонстрация: списание")
        notify("Списание проведено в одной транзакции ✔")
    }

    /** Корректный перевод: 2 счёта + 2 записи журнала в одной транзакции. */
    fun bankTransfer(cents: Long) = run(null) {
        val (a, b) = prepareBank()
        repo.transfer(a.id, b.id, cents, "Живая демонстрация: перевод A → B")
        notify("Перевод ${Money.format(cents)} выполнен одной транзакцией: списание и зачисление вместе ✔")
    }

    /** Антипример: перевод, разорванный на две транзакции. */
    fun bankBrokenTransfer(cents: Long) = runLong {
        val (a, b) = prepareBank()
        _brokenReport.value = repo.brokenTransfer(a.id, b.id, cents)
        _stage.value = null
        notify("⚠ Перевод разорван: списание закоммитилось, зачисление — нет")
    }

    /** Компенсация последствий разорванного перевода. */
    fun compensateBrokenTransfer() = run(null) {
        val report = _brokenReport.value ?: throw WalletException.InvalidAmount()
        val (a, _) = prepareBank()
        repo.deposit(a.id, report.amountCents, "Компенсация разорванного перевода")
        _brokenReport.value = null
        notify("Компенсирующая транзакция вернула ${Money.format(report.amountCents)} на «${a.name}» ✔")
    }

    /** Стресс-тест: 20 параллельных переводов, каждый в своей транзакции. */
    fun runParallelTransfers() = runLong {
        val (a, b) = prepareBank()
        _parallelReport.value = repo.parallelTransfers(a.id, b.id, count = 20, amountCents = 100L)
        _stage.value = null
        notify("Стресс-тест завершён: 20 транзакций, сумма по счетам не изменилась ✔")
    }

    // ─────────────────────────── Вспомогательное ───────────────────────────

    /**
     * Гарантирует наличие двух пользовательских счетов для живых демонстраций.
     * Если пользователь всё удалил — создаём «учебные» счета заново.
     */
    private suspend fun prepareBank(): Pair<AccountEntity, AccountEntity> {
        var list = db.walletDao().getAccountsWithOperations().map { it.account }
        if (list.size >= 2) return enrich(list[0]) to enrich(list[1])

        val startCents = 1_000_000L   // 10 000 ₽
        if (list.isEmpty()) {
            repo.createAccount(
                name = "Учебный счёт A · зарплатный",
                ownerName = "Студент",
                type = AccountType.BANK_CARD,
                initialBalanceCents = startCents,
                note = "Счёт для живых демонстраций ACID"
            )
        }
        repo.createAccount(
            name = "Учебный счёт B · накопительный",
            ownerName = "Студент",
            type = AccountType.SAVINGS,
            initialBalanceCents = startCents,
            note = "Счёт для живых демонстраций ACID"
        )

        list = db.walletDao().getAccountsWithOperations().map { it.account }
        return enrich(list[0]) to enrich(list[1])
    }

    private suspend fun enrich(account: AccountEntity): AccountEntity =
        db.walletDao().findAccountById(account.id) ?: account

    /** Оборачивает действие в корутину с защитой от ошибок (ROLLBACK уже сделан Room). */
    private fun run(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                successMessage?.let { notify(it) }
            } catch (t: Throwable) {
                notify(t.message ?: "Операция не выполнена")
            }
        }
    }

    /** То же, но с индикатором занятости и подписью текущего шага. */
    private fun runLong(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            try {
                block()
            } catch (t: Throwable) {
                notify(t.message ?: "Операция не выполнена")
            } finally {
                _stage.value = null
                _busy.value = false
            }
        }
    }

    fun notify(message: String) {
        _messages.tryEmit(message)
    }
}
