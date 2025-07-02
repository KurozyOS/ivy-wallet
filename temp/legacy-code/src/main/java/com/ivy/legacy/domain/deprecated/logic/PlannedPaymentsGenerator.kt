package com.ivy.wallet.domain.deprecated.logic

import com.ivy.base.legacy.Transaction
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.PlannedPaymentRule
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.incrementDate
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PlannedPaymentsGenerator @Inject constructor(
    private val transactionMapper: TransactionMapper,
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private const val GENERATED_INSTANCES_LIMIT = 72
        // 3 years in seconds (more readable than magic number)
        private const val DEFAULT_END_DATE_OFFSET_SECONDS = 3L * 365L * 24L * 60L * 60L
    }

    suspend fun generate(rule: PlannedPaymentRule) {
        try {
            // Validate rule before processing
            if (!isValidRule(rule)) {
                return
            }

            // Delete all unpaid transactions for this rule to regenerate them
            transactionRepository.deletedByRecurringRuleIdAndNoDateTime(
                recurringRuleId = rule.id
            )

            if (rule.oneTime) {
                generateOneTime(rule)
            } else {
                generateRecurring(rule)
            }
        } catch (e: Exception) {
            // Log error but don't crash the app
            e.printStackTrace()
        }
    }

    private fun isValidRule(rule: PlannedPaymentRule): Boolean {
        return rule.startDate != null &&
                rule.amount > 0 &&
                (rule.oneTime || (rule.intervalN != null && rule.intervalN!! > 0 && rule.intervalType != null))
    }

    private suspend fun generateOneTime(rule: PlannedPaymentRule) {
        val existingTransactions = transactionRepository.findAllByRecurringRuleId(recurringRuleId = rule.id)
        
        // Only generate if no transactions exist for this rule
        if (existingTransactions.isEmpty()) {
            generateTransaction(rule, rule.startDate!!)
        }
    }

    @Suppress("MagicNumber")
    private suspend fun generateRecurring(rule: PlannedPaymentRule) {
        val startDate = rule.startDate!!
        val endDate = rule.endDate ?: startDate.plusSeconds(DEFAULT_END_DATE_OFFSET_SECONDS)
        
        // Validate end date is after start date
        if (!endDate.isAfter(startDate)) {
            return
        }

        val existingTransactions = transactionRepository.findAllByRecurringRuleId(recurringRuleId = rule.id)
        val existingDueDates = existingTransactions
            .filter { !it.settled } // Only consider unsettled transactions
            .mapNotNull { it.time } // Get due dates
            .toSet()

        var generatedTransactions = 0
        var date = startDate

        while (date.isBefore(endDate) && generatedTransactions < GENERATED_INSTANCES_LIMIT) {
            // Check if a transaction for this due date already exists
            val dateRounded = date.truncatedTo(ChronoUnit.DAYS)
            if (!existingDueDates.contains(dateRounded)) {
                try {
                    generateTransaction(rule, date)
                    generatedTransactions++
                } catch (e: Exception) {
                    // Log error for this specific transaction but continue with others
                    e.printStackTrace()
                }
            }

            // Calculate next occurrence
            val intervalN = rule.intervalN!!.toLong()
            date = rule.intervalType!!.incrementDate(
                date = date,
                intervalN = intervalN
            )
        }
    }

    private suspend fun generateTransaction(rule: PlannedPaymentRule, dueDate: Instant) {
        val transaction = Transaction(
            type = rule.type,
            accountId = rule.accountId,
            recurringRuleId = rule.id,
            categoryId = rule.categoryId,
            amount = rule.amount.toBigDecimal(),
            title = rule.title,
            description = rule.description,
            dueDate = dueDate,
            dateTime = null,
            toAccountId = null,
            isSynced = false
        )

        val domainTransaction = transaction.toDomain(transactionMapper)
        if (domainTransaction != null) {
            transactionRepository.save(domainTransaction)
        } else {
            throw IllegalStateException("Failed to convert transaction to domain model for rule ${rule.id}")
        }
    }
}
