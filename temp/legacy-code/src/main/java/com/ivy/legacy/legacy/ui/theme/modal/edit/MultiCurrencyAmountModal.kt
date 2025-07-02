package com.ivy.wallet.ui.theme.modal.edit

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.domain.deprecated.logic.currency.ExchangeRatesLogic
import com.ivy.legacy.utils.amountToDouble
import com.ivy.legacy.utils.amountToDoubleOrNull
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.formatInputAmount
import com.ivy.legacy.utils.hideKeyboard
import com.ivy.legacy.utils.localDecimalSeparator
import com.ivy.legacy.utils.onScreenStart
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalPositiveButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@SuppressLint("ComposeModifierMissing")
@Suppress("ParameterNaming")
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.MultiCurrencyAmountModal(
    id: UUID,
    visible: Boolean,
    initialCurrency: String,
    targetCurrency: String, // The currency we want to convert to (account or base currency)
    baseCurrency: String,
    initialAmount: Double?,
    dismiss: () -> Unit,
    showPlusMinus: Boolean = false,
    decimalCountMax: Int = 2,
    Header: (@Composable () -> Unit)? = null,
    amountSpacerTop: Dp = 48.dp,
    exchangeRatesLogic: ExchangeRatesLogic,
    onAmountChanged: (Double, String) -> Unit, // Returns converted amount and selected currency
) {
    var inputCurrency by remember(id) {
        mutableStateOf(initialCurrency)
    }
    
    var amount by remember(id, inputCurrency) {
        mutableStateOf(
            if (inputCurrency.isNotEmpty()) {
                initialAmount?.takeIf { it != 0.0 }?.format(inputCurrency)
                    ?: ""
            } else {
                initialAmount?.takeIf { it != 0.0 }?.format(decimalCountMax)
                    ?: ""
            }
        )
    }

    var convertedAmount by remember { mutableStateOf<Double?>(null) }
    var currencyModalVisible by remember { mutableStateOf(false) }
    var calculatorModalVisible by remember(id) { mutableStateOf(false) }

    // Convert amount whenever input amount or currency changes
    LaunchedEffect(amount, inputCurrency, targetCurrency) {
        if (amount.isNotBlank() && inputCurrency != targetCurrency) {
            try {
                val inputAmount = amount.amountToDoubleOrNull() ?: 0.0
                if (inputAmount > 0.0) {
                    val converted = withContext(Dispatchers.IO) {
                        exchangeRatesLogic.convertAmount(
                            baseCurrency = baseCurrency,
                            amount = inputAmount,
                            fromCurrency = inputCurrency,
                            toCurrency = targetCurrency
                        )
                    }
                    convertedAmount = converted
                } else {
                    convertedAmount = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                convertedAmount = null
            }
        } else {
            // No conversion needed when currencies are the same or amount is blank
            convertedAmount = null
        }
    }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            IvyIcon(
                modifier = circleButtonModifier(
                    size = 52.dp,
                    onClick = {
                        calculatorModalVisible = true
                    }
                )
                    .testTag("btn_calculator")
                    .padding(all = 4.dp),
                icon = R.drawable.ic_custom_calculator_m,
                tint = UI.colors.pureInverse
            )

            Spacer(Modifier.width(16.dp))

            ModalPositiveButton(
                text = stringResource(R.string.enter),
                iconStart = R.drawable.ic_check
            ) {
                try {
                    val finalAmount = if (inputCurrency == targetCurrency) {
                        // No conversion needed - use the original amount
                        amount.amountToDouble()
                    } else {
                        // Use converted amount if available, otherwise original amount
                        convertedAmount ?: amount.amountToDouble()
                    }
                    onAmountChanged(finalAmount, inputCurrency)
                    dismiss()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        SecondaryActions = {
            if (showPlusMinus) {
                Row {
                    Spacer(modifier = Modifier.width(24.dp))
                    KeypadCircleButton(
                        text = "+/-",
                        testTag = "plus_minus",
                        fontSize = 22.sp,
                        btnSize = 52.dp,
                        onClick = {
                            when {
                                amount.firstOrNull() == '-' -> {
                                    amount = amount.drop(1)
                                }
                                amount.isNotEmpty() -> {
                                    amount = "-$amount"
                                }
                            }
                        }
                    )
                }
            }
        }
    ) {
        Header?.invoke()

        Spacer(Modifier.height(amountSpacerTop))

        val rootView = LocalView.current
        onScreenStart {
            hideKeyboard(rootView)
        }

        // Currency Selector
        CurrencySelector(
            currency = inputCurrency,
            onClick = { currencyModalVisible = true }
        )

        Spacer(Modifier.height(12.dp))

        // Input Amount Display
        AmountCurrency(
            amount = amount,
            currency = inputCurrency
        )

        // Conversion Display
        if (inputCurrency != targetCurrency && convertedAmount != null) {
            Spacer(Modifier.height(8.dp))
            ConversionDisplay(
                convertedAmount = convertedAmount!!,
                targetCurrency = targetCurrency
            )
        }

        Spacer(Modifier.height(16.dp))

        AmountInput(
            currency = inputCurrency,
            decimalCountMax = decimalCountMax,
            amount = amount
        ) { newAmount ->
            amount = newAmount
        }

        Spacer(Modifier.height(24.dp))
    }

    // Currency Selection Modal
    CurrencyModal(
        title = stringResource(R.string.choose_currency),
        initialCurrency = IvyCurrency.fromCode(inputCurrency),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) { newCurrency ->
        inputCurrency = newCurrency
        // Reset amount formatting for new currency
        amount = if (amount.isNotBlank()) {
            val amountValue = amount.amountToDoubleOrNull() ?: 0.0
            if (amountValue > 0.0) {
                amountValue.format(newCurrency)
            } else ""
        } else ""
    }

    CalculatorModal(
        visible = calculatorModalVisible,
        initialAmount = amount.amountToDoubleOrNull(),
        currency = inputCurrency,
        dismiss = {
            calculatorModalVisible = false
        },
        onCalculation = { calculatedAmount ->
            amount = if (inputCurrency.isNotEmpty()) calculatedAmount.format(inputCurrency) else calculatedAmount.format(decimalCountMax)
        }
    )
}

@Composable
private fun CurrencySelector(
    currency: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IvyIcon(
                icon = R.drawable.ic_currency,
                tint = UI.colors.pureInverse
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.input_currency),
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                )
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = currency.ifBlank { "---" },
                style = UI.typo.b1.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.ExtraBold
                )
            )
            Spacer(Modifier.width(8.dp))
            IvyIcon(
                icon = R.drawable.ic_arrow_right,
                tint = UI.colors.pureInverse
            )
        }
    }
}

@Composable
private fun ConversionDisplay(
    convertedAmount: Double,
    targetCurrency: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "≈",
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Bold
            )
        )
        
        Spacer(Modifier.height(2.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = convertedAmount.format(targetCurrency),
                style = UI.typo.nH2.style(
                    fontWeight = FontWeight.Medium,
                    color = Orange
                )
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = targetCurrency,
                style = UI.typo.b1.style(
                    fontWeight = FontWeight.Normal,
                    color = Orange
                )
            )
        }
    }
}

@SuppressLint("ComposableModifierFactory", "ModifierFactoryExtensionFunction")
@Composable
private fun circleButtonModifier(
    size: Dp = 80.dp,
    onClick: () -> Unit,
): Modifier {
    return Modifier
        .clip(CircleShape)
        .clickable(onClick = onClick)
        .background(UI.colors.pure, UI.shapes.rFull)
        .border(2.dp, UI.colors.medium, UI.shapes.rFull)
        .padding(size / 6)
}

