package com.ivy.planned.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.model.Category
import com.ivy.data.model.CategoryId
import com.ivy.data.model.IntervalType
import com.ivy.data.repository.CategoryRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.datamodel.temp.toLegacyDomain
import com.ivy.legacy.domain.deprecated.logic.AccountCreator
import com.ivy.legacy.utils.ioThread
import com.ivy.navigation.EditPlannedScreen
import com.ivy.navigation.Navigation
import com.ivy.ui.ComposeViewModel
import com.ivy.wallet.domain.action.account.AccountsAct
import com.ivy.wallet.domain.deprecated.logic.CategoryCreator
import com.ivy.wallet.domain.deprecated.logic.PlannedPaymentsGenerator
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.domain.deprecated.logic.model.CreateCategoryData
import com.ivy.wallet.ui.theme.modal.DeletePlannedPaymentOption
import com.ivy.wallet.ui.theme.modal.RecurringRuleModalData
import com.ivy.wallet.ui.theme.modal.edit.AccountModalData
import com.ivy.wallet.ui.theme.modal.edit.CategoryModalData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@Stable
@HiltViewModel
class EditPlannedViewModel @Inject constructor(
    private val accountDao: AccountDao,
    private val categoryRepository: CategoryRepository,
    private val settingsDao: SettingsDao,
    private val nav: Navigation,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val plannedPaymentsGenerator: PlannedPaymentsGenerator,
    private val categoryCreator: CategoryCreator,
    private val accountCreator: AccountCreator,
    private val accountsAct: AccountsAct,
    private val plannedPaymentRuleWriter: WritePlannedPaymentRuleDao,
    private val transactionRepository: TransactionRepository,
    private val timeConverter: TimeConverter,
) : ComposeViewModel<EditPlannedScreenState, EditPlannedScreenEvent>() {

    private var transactionType by mutableStateOf(TransactionType.INCOME)
    private var startDate by mutableStateOf<LocalDateTime?>(null)
    private var endDate by mutableStateOf<LocalDateTime?>(null)
    private var intervalN by mutableStateOf<Int?>(null)
    private var intervalType by mutableStateOf<IntervalType?>(null)
    private var oneTime by mutableStateOf(false)
    private var initialTitle by mutableStateOf<String?>(null)
    private var description by mutableStateOf<String?>(null)
    private var account by mutableStateOf<Account?>(null)
    private var category by mutableStateOf<Category?>(null)
    private var amount by mutableDoubleStateOf(0.0)
    private var currency by mutableStateOf("")
    private var categories by mutableStateOf<ImmutableList<Category>>(persistentListOf())
    private var accounts by mutableStateOf<ImmutableList<Account>>(persistentListOf())
    private var categoryModalVisible by mutableStateOf(false)
    private var descriptionModalVisible by mutableStateOf(false)
    private var deleteTransactionModalVisible by mutableStateOf(false)
    private var transactionTypeModalVisible by mutableStateOf(false)
    private var amountModalVisible by mutableStateOf(false)
    private var recurringRuleModalData by mutableStateOf<RecurringRuleModalData?>(null)
    private var categoryModalData by mutableStateOf<CategoryModalData?>(null)
    private var accountModalData by mutableStateOf<AccountModalData?>(null)

    private var loadedRule: PlannedPaymentRule? = null
    private var editMode = false
    private var title: String? = null

    @Composable
    override fun uiState(): EditPlannedScreenState {
        return EditPlannedScreenState(
            currency = getCurrency(),
            categories = getCategories(),
            accounts = getAccounts(),
            transactionType = getTransactionType(),
            startDate = getStartDate(),
            endDate = getEndDate(),
            intervalN = getIntervalN(),
            oneTime = getOneTime(),
            account = getAccount(),
            category = getCategory(),
            amount = getAmount(),
            initialTitle = getInitialTitle(),
            description = getDescription(),
            intervalType = getIntervalType(),
            categoryModalVisible = getCategoryModalVisibility(),
            categoryModalData = getCategoryModalData(),
            accountModalData = getAccountModalData(),
            deleteTransactionModalVisible = getDeleteTransactionModalVisibility(),
            descriptionModalVisible = getDescriptionModalVisibility(),
            amountModalVisible = getAmountModalVisibility(),
            transactionTypeModalVisible = getTransactionTypeModalVisibility(),
            recurringRuleModalData = getRecurringRuleModalData()
        )
    }

    @Composable
    private fun getCurrency(): String {
        return currency
    }

    @Composable
    private fun getCategories(): ImmutableList<Category> {
        return categories
    }

    @Composable
    private fun getAccounts(): ImmutableList<Account> {
        return accounts
    }

    @Composable
    private fun getTransactionType(): TransactionType {
        return transactionType
    }

    @Composable
    private fun getStartDate(): LocalDateTime? {
        return startDate
    }

    @Composable
    private fun getEndDate(): LocalDateTime? {
        return endDate
    }

    @Composable
    private fun getIntervalN(): Int? {
        return intervalN
    }

    @Composable
    private fun getIntervalType(): IntervalType? {
        return intervalType
    }

    @Composable
    private fun getOneTime(): Boolean {
        return oneTime
    }

    @Composable
    private fun getInitialTitle(): String? {
        return initialTitle
    }

    @Composable
    private fun getDescription(): String? {
        return description
    }

    @Composable
    private fun getAccount(): Account? {
        return account
    }

    @Composable
    private fun getCategory(): Category? {
        return category
    }

    @Composable
    private fun getAmount(): Double {
        return amount
    }

    @Composable
    private fun getCategoryModalVisibility(): Boolean {
        return categoryModalVisible
    }

    @Composable
    private fun getDescriptionModalVisibility(): Boolean {
        return descriptionModalVisible
    }

    @Composable
    private fun getDeleteTransactionModalVisibility(): Boolean {
        return deleteTransactionModalVisible
    }

    @Composable
    private fun getTransactionTypeModalVisibility(): Boolean {
        return transactionTypeModalVisible
    }

    @Composable
    private fun getAmountModalVisibility(): Boolean {
        return amountModalVisible
    }

    @Composable
    private fun getCategoryModalData(): CategoryModalData? {
        return categoryModalData
    }

    @Composable
    private fun getAccountModalData(): AccountModalData? {
        return accountModalData
    }

    @Composable
    private fun getRecurringRuleModalData(): RecurringRuleModalData? {
        return recurringRuleModalData
    }

    override fun onEvent(event: EditPlannedScreenEvent) {
        when (event) {
            is EditPlannedScreenEvent.OnSave -> save()
            is EditPlannedScreenEvent.OnDelete -> delete()
            is EditPlannedScreenEvent.OnDeleteWithOption -> deleteWithOption(event.option)
            is EditPlannedScreenEvent.OnSetTransactionType ->
                updateTransactionType(event.newTransactionType)

            is EditPlannedScreenEvent.OnDescriptionChanged ->
                updateDescription(event.newDescription)

            is EditPlannedScreenEvent.OnCreateAccount -> createAccount(event.data)
            is EditPlannedScreenEvent.OnCreateCategory -> createCategory(event.data)
            is EditPlannedScreenEvent.OnAccountChanged -> updateAccount(event.newAccount)
            is EditPlannedScreenEvent.OnAmountChanged -> updateAmount(event.newAmount)
            is EditPlannedScreenEvent.OnTitleChanged -> updateTitle(event.newTitle)
            is EditPlannedScreenEvent.OnRuleChanged ->
                updateRule(event.startDate, event.endDate, event.oneTime, event.intervalN, event.intervalType)

            is EditPlannedScreenEvent.OnCategoryChanged -> updateCategory(event.newCategory)
            is EditPlannedScreenEvent.OnEditCategory -> editCategory(event.updatedCategory)
            is EditPlannedScreenEvent.OnCategoryModalVisible ->
                categoryModalVisible = event.visible

            is EditPlannedScreenEvent.OnCategoryModalDataChanged ->
                categoryModalData = event.categoryModalData

            is EditPlannedScreenEvent.OnAccountModalDataChanged ->
                accountModalData = event.accountModalData

            is EditPlannedScreenEvent.OnDescriptionModalVisible ->
                descriptionModalVisible = event.visible

            is EditPlannedScreenEvent.OnTransactionTypeModalVisible ->
                transactionTypeModalVisible = event.visible

            is EditPlannedScreenEvent.OnAmountModalVisible ->
                amountModalVisible = event.visible

            is EditPlannedScreenEvent.OnDeleteTransactionModalVisible ->
                deleteTransactionModalVisible = event.visible

            is EditPlannedScreenEvent.OnRecurringRuleModalDataChanged ->
                recurringRuleModalData = event.recurringRuleModalData
        }
    }

    fun start(screen: EditPlannedScreen) {
        viewModelScope.launch {
            transactionType = screen.type
            editMode = screen.plannedPaymentRuleId != null

            val accounts = accountsAct(Unit)
            if (accounts.isEmpty()) {
                nav.back()
                return@launch
            }
            this@EditPlannedViewModel.accounts = accounts
            categories = categoryRepository.findAll().toImmutableList()

            reset()

            loadedRule = screen.plannedPaymentRuleId?.let {
                ioThread { plannedPaymentRuleDao.findById(it)!!.toLegacyDomain() }
            } ?: PlannedPaymentRule(
                startDate = null,
                intervalN = null,
                intervalType = null,
                oneTime = false,
                type = screen.type,
                amount = screen.amount ?: 0.0,
                accountId = screen.accountId ?: accounts.first().id,
                categoryId = screen.categoryId,
                title = screen.title,
                description = screen.description
            )

            display(loadedRule!!)
        }
    }

    private suspend fun display(rule: PlannedPaymentRule) {
        this.title = rule.title

        transactionType = rule.type
        startDate = with(timeConverter) { rule.startDate?.toLocalDateTime() }
        endDate = with(timeConverter) { rule.endDate?.toLocalDateTime() }
        intervalN = rule.intervalN
        oneTime = rule.oneTime
        intervalType = rule.intervalType
        initialTitle = rule.title
        description = rule.description
        val selectedAccount = ioThread { accountDao.findById(rule.accountId)!!.toLegacyDomain() }
        account = selectedAccount
        category = rule.categoryId?.let {
            ioThread { categoryRepository.findById(CategoryId(it)) }
        }
        amount = rule.amount

        updateCurrency(account = selectedAccount)
    }

    private suspend fun updateCurrency(account: Account) {
        currency = account.currency ?: baseCurrency()
    }

    private suspend fun baseCurrency(): String = ioThread { settingsDao.findFirst().currency }

    private fun updateRule(
        startDate: LocalDateTime,
        endDate: LocalDateTime?,
        oneTime: Boolean,
        intervalN: Int?,
        intervalType: IntervalType?
    ) {
        loadedRule = loadedRule().copy(
            startDate = with(timeConverter) { startDate.toUTC() },
            endDate = endDate?.let { with(timeConverter) { it.toUTC() } },
            intervalN = intervalN,
            intervalType = intervalType,
            oneTime = oneTime
        )
        this@EditPlannedViewModel.startDate = startDate
        this@EditPlannedViewModel.endDate = endDate
        this@EditPlannedViewModel.intervalN = intervalN
        this@EditPlannedViewModel.intervalType = intervalType
        this@EditPlannedViewModel.oneTime = oneTime

        saveIfEditMode()
    }

    private fun updateAmount(newAmount: Double) {
        loadedRule = loadedRule().copy(
            amount = newAmount
        )
        this@EditPlannedViewModel.amount = newAmount

        saveIfEditMode()
    }

    private fun updateTitle(newTitle: String?) {
        loadedRule = loadedRule().copy(
            title = newTitle
        )
        this.title = newTitle

        saveIfEditMode()
    }

    private fun updateDescription(newDescription: String?) {
        loadedRule = loadedRule().copy(
            description = newDescription
        )
        this@EditPlannedViewModel.description = newDescription

        saveIfEditMode()
    }

    private fun updateCategory(newCategory: Category?) {
        loadedRule = loadedRule().copy(
            categoryId = newCategory?.id?.value
        )
        this@EditPlannedViewModel.category = newCategory

        saveIfEditMode()
    }

    private fun updateAccount(newAccount: Account) {
        loadedRule = loadedRule().copy(
            accountId = newAccount.id
        )
        this@EditPlannedViewModel.account = newAccount

        viewModelScope.launch {
            updateCurrency(account = newAccount)
        }

        saveIfEditMode()
    }

    private fun updateTransactionType(newTransactionType: TransactionType) {
        loadedRule = loadedRule().copy(
            type = newTransactionType
        )
        this@EditPlannedViewModel.transactionType = newTransactionType

        saveIfEditMode()
    }

    private fun saveIfEditMode() {
        if (editMode) {
            save(false)
        }
    }

    private fun save(closeScreen: Boolean = true) {
        val validationResult = validateWithDetails()
        if (!validationResult.isValid) {
            // Show validation error to user
            showError(validationResult.errorMessage)
            return
        }

        viewModelScope.launch {
            try {
                val saveResult = ioThread {
                    try {
                        loadedRule = loadedRule().copy(
                            type = transactionType ?: throw IllegalStateException("Transaction type is required"),
                            startDate = startDate?.let { with(timeConverter) { it.toUTC() } }
                                ?: throw IllegalStateException("Start date is required"),
                            endDate = endDate?.let { with(timeConverter) { it.toUTC() } },
                            intervalN = intervalN,
                            intervalType = intervalType,
                            categoryId = category?.id?.value,
                            accountId = account?.id ?: throw IllegalStateException("Account is required"),
                            title = title?.trim()?.takeIf { it.isNotBlank() },
                            description = description?.trim()?.takeIf { it.isNotBlank() },
                            amount = amount ?: throw IllegalStateException("Amount is required"),

                            isSynced = false
                        )

                        plannedPaymentRuleWriter.save(loadedRule().toEntity())
                        plannedPaymentsGenerator.generate(loadedRule())
                        true // Success
                    } catch (e: IllegalStateException) {
                        showError("Please fill in all required fields: ${e.message}")
                        false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        showError("Failed to save planned payment. Please try again.")
                        false
                    }
                }

                if (saveResult && closeScreen) {
                    nav.back()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showError("An unexpected error occurred. Please try again.")
            }
        }
    }

    private fun showError(message: String) {
        // TODO: Implement proper error display mechanism
        // For now, we'll just print to console
        // In a real implementation, this would show a snackbar or toast
        println("EditPlannedViewModel Error: $message")
    }

    private fun validate(): Boolean {
        return validateWithDetails().isValid
    }

    private fun validateWithDetails(): ValidationResult {
        // Check transaction type
        if (transactionType == TransactionType.TRANSFER) {
            return ValidationResult(false, "Transfer transactions are not supported for planned payments")
        }

        // Check amount
        val currentAmount = amount
        if (currentAmount == null || currentAmount <= 0.0) {
            return ValidationResult(false, "Amount must be greater than zero")
        }

        // Check account
        if (account == null) {
            return ValidationResult(false, "Please select an account")
        }

        return if (oneTime) validateOneTimeWithDetails() else validateRecurringWithDetails()
    }

    private fun validateOneTimeWithDetails(): ValidationResult {
        if (startDate == null) {
            return ValidationResult(false, "Please select a start date")
        }

        return ValidationResult(true, "Valid")
    }

    private fun validateRecurringWithDetails(): ValidationResult {
        if (startDate == null) {
            return ValidationResult(false, "Please select a start date")
        }

        if (intervalN == null || intervalN!! <= 0) {
            return ValidationResult(false, "Interval must be greater than zero")
        }

        if (intervalType == null) {
            return ValidationResult(false, "Please select an interval type")
        }

        // Validate end date if provided
        endDate?.let { end ->
            if (!end.isAfter(startDate)) {
                return ValidationResult(false, "End date must be after start date")
            }
        }

        return ValidationResult(true, "Valid")
    }

    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String
    )

    // Legacy validation methods for backward compatibility
    private fun validateOneTime(): Boolean {
        return validateOneTimeWithDetails().isValid
    }

    private fun validateRecurring(): Boolean {
        return validateRecurringWithDetails().isValid
    }

    private fun delete() {
        viewModelScope.launch {
            deleteTransactionModalVisible = false
            ioThread {
                loadedRule?.let {
                    plannedPaymentRuleWriter.deleteById(it.id)
                    transactionRepository.deletedByRecurringRuleIdAndNoDateTime(
                        recurringRuleId = it.id
                    )
                }
                nav.back()
            }
        }
    }

    private fun deleteWithOption(option: DeletePlannedPaymentOption) {
        viewModelScope.launch {
            deleteTransactionModalVisible = false
            ioThread {
                loadedRule?.let { rule ->
                    when (option) {
                        DeletePlannedPaymentOption.CURRENT_ONLY -> {
                            // For current only, we need to find and delete only the current/next due transaction
                            // while keeping the recurring rule intact
                            val currentTransactions = transactionRepository.findAllByRecurringRuleId(
                                recurringRuleId = rule.id
                            )
                            
                            // Find the next due transaction (not yet paid)
                            val nextDueTransaction = currentTransactions
                                .filter { !it.settled }
                                .minByOrNull { it.time }
                            
                            nextDueTransaction?.let { transactionToDelete ->
                                transactionRepository.deleteById(transactionToDelete.id)
                            }
                        }
                        DeletePlannedPaymentOption.ALL_FUTURE -> {
                            // Delete the entire rule and all associated transactions
                            plannedPaymentRuleWriter.deleteById(rule.id)
                            transactionRepository.deletedByRecurringRuleIdAndNoDateTime(
                                recurringRuleId = rule.id
                            )
                        }
                    }
                }
                nav.back()
            }
        }
    }

    private fun createCategory(data: CreateCategoryData) {
        viewModelScope.launch {
            categoryCreator.createCategory(data, onRefreshUI = {
                categories = categoryRepository.findAll().toImmutableList()
            })
        }
    }

    private fun editCategory(updatedCategory: Category) {
        viewModelScope.launch {
            categoryCreator.editCategory(updatedCategory, onRefreshUI = {
                categories = categoryRepository.findAll().toImmutableList()
            })
        }
    }

    private fun createAccount(data: CreateAccountData) {
        viewModelScope.launch {
            accountCreator.createAccount(data) {
                accounts = accountsAct(Unit)
            }
        }
    }

    private fun reset() {
        loadedRule = null

        initialTitle = null
        description = null
        category = null
    }

    private fun loadedRule() = loadedRule ?: error("Loaded transaction is null")
}
