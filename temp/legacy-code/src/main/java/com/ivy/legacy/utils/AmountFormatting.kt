package com.ivy.legacy.utils

import com.ivy.wallet.domain.data.IvyCurrency
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.truncate

const val MILLION = 1000000
const val N_100K = 100000
const val THOUSAND = 1000

// Maximum amount to prevent overflow issues
private const val MAX_AMOUNT = 1_000_000_000_000.0 // 1 trillion
private const val MIN_AMOUNT = -1_000_000_000_000.0 // -1 trillion

fun String.amountToDoubleOrNull(): Double? {
    return try {
        val normalized = this.normalizeAmount()
        if (normalized.isBlank()) return null
        
        // Additional validation to prevent malicious input
        if (normalized.length > 20) return null // Prevent extremely long strings
        if (normalized.count { it == '.' } > 1) return null // Multiple decimal points
        
        val result = normalized.toDoubleOrNull()
        
        // Validate bounds and check for special values
        when {
            result == null -> null
            result.isNaN() || result.isInfinite() -> null
            result > MAX_AMOUNT || result < MIN_AMOUNT -> null
            else -> result
        }
    } catch (e: Exception) {
        null
    }
}

fun String.amountToDouble(): Double {
    return this.amountToDoubleOrNull() ?: 0.0
}

fun String.normalizeAmount(): String {
    return this.trim()
        .removeGroupingSeparator()
        .normalizeDecimalSeparator()
        .sanitizeNumericInput()
}

fun String.normalizeExpression(): String {
    return this.trim()
        .removeGroupingSeparator()
        .normalizeDecimalSeparator()
        .sanitizeExpressionInput()
}

private fun String.sanitizeNumericInput(): String {
    // Remove any non-numeric characters except decimal point and minus sign
    return this.filter { it.isDigit() || it == '.' || it == '-' }
        .let { filtered ->
            // Ensure only one minus sign at the beginning
            val minusCount = filtered.count { it == '-' }
            when {
                minusCount == 0 -> filtered
                minusCount == 1 && filtered.startsWith('-') -> filtered
                else -> filtered.replace("-", "").let { if (minusCount > 0) "-$it" else it }
            }
        }
}

private fun String.sanitizeExpressionInput(): String {
    // For calculator expressions, allow basic math operations
    return this.filter { 
        it.isDigit() || it == '.' || it == '-' || it == '+' || it == '*' || it == '/' || it == '(' || it == ')' || it == ' '
    }.trim()
}

fun String.removeGroupingSeparator(): String {
    return replace(localGroupingSeparator(), "")
}

fun String.normalizeDecimalSeparator(): String {
    return replace(localDecimalSeparator(), ".")
}

fun localDecimalSeparator(): String {
    return DecimalFormatSymbols.getInstance().decimalSeparator.toString()
}

fun localGroupingSeparator(): String {
    return DecimalFormatSymbols.getInstance().groupingSeparator.toString()
}

// Display Formatting
fun Double.format(digits: Int) = "%.${digits}f".format(this)

fun Double.format(currencyCode: String): String {
    return this.format(IvyCurrency.fromCode(currencyCode))
}

fun Double.format(currency: IvyCurrency?): String {
    return if (currency?.isCrypto == true) {
        val result = this.formatCrypto()
        return when {
            result.lastOrNull() == localDecimalSeparator().firstOrNull() -> {
                val newResult = result.dropLast(1)
                if (newResult.isEmpty()) "0" else newResult
            }

            result.isEmpty() -> {
                "0"
            }

            else -> result
        }
    } else {
        formatFIAT()
    }
}

fun Double.formatCrypto(): String {
    return try {
        val pattern = "###,###,##0.${"0".repeat(9)}"
        val format = DecimalFormat(pattern)
        val numberStringWithZeros = format.format(this)

        var lastTrailingZeroIndex: Int? = null
        for (i in numberStringWithZeros.lastIndex.downTo(0)) {
            if (numberStringWithZeros[i] == '0') {
                lastTrailingZeroIndex = i
            } else {
                break
            }
        }

        if (lastTrailingZeroIndex != null) {
            numberStringWithZeros.substring(0, lastTrailingZeroIndex)
        } else {
            numberStringWithZeros
        }
    } catch (e: Exception) {
        "0"
    }
}

private fun Double.formatFIAT(): String {
    return try {
        DecimalFormat("#,##0.00").format(this)
    } catch (e: Exception) {
        "0.00"
    }
}

fun shortenAmount(amount: Double): String {
    return when {
        abs(amount) >= MILLION -> {
            formatShortenedNumber(amount / MILLION, "m")
        }

        abs(amount) >= THOUSAND -> {
            formatShortenedNumber(amount / THOUSAND, "k")
        }

        else -> amount.toString()
    }
}

private fun formatShortenedNumber(
    number: Double,
    extension: String,
): String {
    return if (hasSignificantDecimalPart(number)) {
        "${number.format(2)}$extension"
    } else {
        "${number.toInt()}$extension"
    }
}

fun hasSignificantDecimalPart(number: Double): Boolean {
    // TODO: Review, might cause trouble when integrating crypto
    val intPart = number.toInt()
    return abs(number - intPart) >= 0.009
}

fun shouldShortAmount(amount: Double): Boolean {
    return abs(amount) >= N_100K
}

fun formatInt(number: Int): String {
    return try {
        DecimalFormat("#,###,###,###").format(number)
    } catch (e: Exception) {
        number.toString()
    }
}

fun decimalPartFormatted(currency: String, value: Double): String {
    return try {
        if (IvyCurrency.fromCode(currency)?.isCrypto == true) {
            val decimalPartFormatted = value.formatCrypto()
                .split(localDecimalSeparator())
                .getOrNull(1) ?: "null"
            if (decimalPartFormatted.isNotBlank()) {
                "${localDecimalSeparator()}$decimalPartFormatted"
            } else {
                ""
            }
        } else {
            "${localDecimalSeparator()}${decimalPartFormattedFIAT(value)}"
        }
    } catch (e: Exception) {
        ""
    }
}

private fun decimalPartFormattedFIAT(value: Double): String {
    return try {
        DecimalFormat(".00").format(value)
            .split(localDecimalSeparator())
            .getOrNull(1)
            ?: value.toString()
                .split(localDecimalSeparator())
                .getOrNull(1)
            ?: "null"
    } catch (e: Exception) {
        "00"
    }
}

fun Long.length() = when (this) {
    0L -> 1
    else -> log10(abs(toDouble())).toInt() + 1
}

fun formatInputAmount(
    currency: String,
    amount: String,
    newSymbol: String,
    decimalCountMax: Int = 2,
): String? {
    return try {
        // Validate inputs
        if (newSymbol.length != 1 || !newSymbol.first().isDigit()) return null
        if (amount.length > 15) return null // Prevent extremely long inputs
        
        val newlyEnteredNumberString = amount + newSymbol

        val decimalPartString = newlyEnteredNumberString
            .split(localDecimalSeparator())
            .getOrNull(1)
        val decimalCount = decimalPartString?.length ?: 0

        val amountDouble = newlyEnteredNumberString.amountToDoubleOrNull()

        val decimalCountOkay = IvyCurrency.fromCode(currency)?.isCrypto == true ||
                decimalCount <= decimalCountMax
                
        if (amountDouble != null && decimalCountOkay && amountDouble >= 0) {
            val intPart = truncate(amountDouble).toInt()
            val decimalPartFormatted = if (decimalPartString != null) {
                "${localDecimalSeparator()}$decimalPartString"
            } else {
                ""
            }

            return formatInt(intPart) + decimalPartFormatted
        }

        null
    } catch (e: Exception) {
        null
    }
}

/**
toInt on numbers in the range (-1.0, 0.0) (exclusive of boundaries) will produce a positive int 0
So, this function append negative sign in that case
 */
fun integerPartFormatted(value: Double): String {
    val preciseValue = value.toBigDecimal()
    val formattedValue = DecimalFormat("###,###").format(preciseValue.toInt())
    return if (value > -1.0 && value < 0.0) {
        "-$formattedValue"
    } else {
        formattedValue
    }
}
