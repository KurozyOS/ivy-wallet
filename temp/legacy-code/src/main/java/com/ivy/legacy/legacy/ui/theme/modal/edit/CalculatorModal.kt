package com.ivy.wallet.ui.theme.modal.edit

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.utils.amountToDoubleOrNull
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.formatInputAmount
import com.ivy.legacy.utils.localDecimalSeparator
import com.ivy.legacy.utils.localGroupingSeparator
import com.ivy.legacy.utils.normalizeExpression
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalSet
import com.ivy.wallet.ui.theme.modal.ModalTitle
import com.notkamui.keval.Keval
import java.util.UUID

@SuppressLint("ComposeModifierMissing")
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.CalculatorModal(
      initialAmount: Double?,
      visible: Boolean,
      currency: String,
      dismiss: () -> Unit,
      id: UUID = UUID.randomUUID(),
      onCalculation: (Double) -> Unit
) {
    var expression by remember(id, initialAmount) {
        mutableStateOf(initialAmount?.format(currency) ?: "")
    }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalSet(
                modifier = Modifier.testTag("calc_set")
            ) {
                val result = calculate(expression)
                if (result != null) {
                    onCalculation(result)
                    dismiss()
                }
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(text = stringResource(R.string.calculator))

        Spacer(Modifier.height(32.dp))

        val isEmpty = expression.isBlank()
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            text = if (isEmpty) stringResource(R.string.calculator_empty_expression) else expression,
            style = UI.typo.nH2.style(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (isEmpty) Gray else UI.colors.pureInverse
            )
        )

        Spacer(Modifier.height(32.dp))

        AmountKeyboard(
            forCalculator = true,
            ZeroRow = {
                KeypadCircleButton(
                    text = "C",
                    textColor = Red,
                    testTag = "key_C"
                ) {
                    expression = ""
                }

                KeypadCircleButton(
                    text = "(",
                    testTag = "key_("
                ) {
                    expression += "("
                }

                KeypadCircleButton(
                    text = ")",
                    testTag = "key_)"
                ) {
                    expression += ")"
                }

                KeypadCircleButton(
                    text = "÷",
                    testTag = "key_/"
                ) {
                    expression = handleOperator(expression, "÷")
                }
            },
            FirstRowExtra = {
                KeypadCircleButton(
                    text = "×",
                    testTag = "key_*"
                ) {
                    expression = handleOperator(expression, "×")
                }
            },
            SecondRowExtra = {
                KeypadCircleButton(
                    text = "−",
                    testTag = "key_-"
                ) {
                    expression = handleOperator(expression, "−")
                }
            },
            ThirdRowExtra = {
                KeypadCircleButton(
                    text = "+",
                    testTag = "key_+"
                ) {
                    expression = handleOperator(expression, "+")
                }
            },
            FourthRowExtra = {
                KeypadCircleButton(
                    text = "=",
                    testTag = "key_="
                ) {
                    val result = calculate(expression)
                    if (result != null) {
                        expression = result.format(currency)
                    }
                }
            },

            onNumberPressed = {
                expression = formatExpression(
                    expression = expression + it,
                    currency = currency
                )
            },
            onDecimalPoint = {
                expression = formatExpression(
                    expression = expression + localDecimalSeparator(),
                    currency = currency
                )
            },
            onBackspace = {
                if (expression.isNotEmpty()) {
                    expression = expression.dropLast(1)
                }
            }
        )

        Spacer(Modifier.height(24.dp))
    }
}

private fun handleOperator(expression: String, operator: String): String {
    return if (expression.isNotEmpty() && expression.last().isOperator()) {
        expression.dropLast(1) + operator
    } else {
        expression + operator
    }
}

fun Char.isOperator(): Boolean = when (this) {
    '+', '−', '×', '÷' -> true
    else -> false
}

private fun formatExpression(expression: String, currency: String): String {
    var formattedExpression = expression

    expression
        .split("(", ")", "÷", "×", "−", "+")
        .ifEmpty {
            // handle only number expression formatting
            listOf(expression)
        }
        .forEach { part ->
            val numberPart = part.amountToDoubleOrNull()
            if (numberPart != null) {
                val formattedPart = formatInputAmount(
                    currency = currency,
                    amount = part,
                    newSymbol = ""
                )

                if (formattedPart != null) {
                    formattedExpression = formattedExpression.replace(part, formattedPart)
                }
            }
        }

    return formattedExpression
}

private fun calculate(expression: String): Double? {
    return try {
        // Validate and sanitize expression before evaluation
        if (!isValidExpression(expression)) {
            return null
        }
        
        val sanitizedExpression = sanitizeExpression(expression)
        
        // Convert calculator symbols to standard math symbols
        val normalizedExpression = buildString {
            for (char in sanitizedExpression) {
                when (char) {
                    '÷' -> this.append('/')
                    '×' -> this.append('*')
                    '−' -> this.append('-')
                    else -> this.append(char)
                }
            }
        }
        
        val modifiedExpression = if (normalizedExpression.startsWith("-")) {
            "0$normalizedExpression"
        } else {
            normalizedExpression
        }
        
        val result = Keval.eval(modifiedExpression.normalizeExpression())
        
        // Validate result
        when {
            result.isNaN() || result.isInfinite() -> null
            result > 1_000_000_000_000.0 || result < -1_000_000_000_000.0 -> null // Prevent overflow
            else -> result
        }
    } catch (e: Exception) {
        null
    }
}

private fun isValidExpression(expression: String): Boolean {
    // Basic validation checks
    if (expression.isBlank() || expression.length > 100) return false
    
    // Check for balanced parentheses
    var openParens = 0
    for (char in expression) {
        when (char) {
            '(' -> openParens++
            ')' -> {
                openParens--
                if (openParens < 0) return false
            }
        }
    }
    if (openParens != 0) return false
    
    // Check for valid characters only
    val validChars = "0123456789.()÷×−+${localDecimalSeparator()}${localGroupingSeparator()} "
    if (!expression.all { it in validChars }) return false
    
    // Prevent consecutive operators (except for negative numbers)
    val operators = "÷×−+"
    for (i in 0 until expression.length - 1) {
        if (expression[i] in operators && expression[i + 1] in operators) {
            // Allow negative numbers like "5+-3" but not "5++3"
            if (!(expression[i] == '+' && expression[i + 1] == '−')) {
                return false
            }
        }
    }
    
    return true
}

private fun sanitizeExpression(expression: String): String {
    return expression
        .trim()
        .replace(localGroupingSeparator(), "") // Remove grouping separators
        .replace(Regex("\\s+"), "") // Remove extra spaces
        .let { cleaned ->
            // Ensure expression doesn't start/end with operators (except minus for negative numbers)
            val trimmedOperators = "÷×+"
            var result = cleaned
            while (result.isNotEmpty() && result.last() in trimmedOperators) {
                result = result.dropLast(1)
            }
            result
        }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        CalculatorModal(
            visible = true,
            initialAmount = 50.23,
            currency = "BGN",
            dismiss = { },
            onCalculation = {}
        )
    }
}
