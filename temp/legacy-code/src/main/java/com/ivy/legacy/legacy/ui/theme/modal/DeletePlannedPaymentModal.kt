package com.ivy.wallet.ui.theme.modal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.IvyWalletPreview
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.components.IvyIcon
import java.util.UUID

enum class DeletePlannedPaymentOption {
    CURRENT_ONLY,
    ALL_FUTURE
}

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.DeletePlannedPaymentModal(
    visible: Boolean,
    isRecurring: Boolean,
    dismiss: () -> Unit,
    id: UUID = UUID.randomUUID(),
    onDelete: (DeletePlannedPaymentOption) -> Unit,
) {
    var selectedOption by remember { mutableStateOf(DeletePlannedPaymentOption.ALL_FUTURE) }

    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            ModalNegativeButton(
                text = stringResource(R.string.delete),
                iconStart = R.drawable.ic_delete
            ) {
                onDelete(selectedOption)
            }
        }
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = if (isRecurring) {
                stringResource(R.string.delete_recurring_payment)
            } else {
                stringResource(R.string.confirm_deletion)
            },
            style = UI.typo.b1.style(
                color = Red,
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.height(24.dp))

        if (isRecurring) {
            // Show options for recurring payments
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                DeleteOptionCard(
                    title = stringResource(R.string.delete_current_payment_only),
                    description = stringResource(R.string.delete_current_payment_description),
                    selected = selectedOption == DeletePlannedPaymentOption.CURRENT_ONLY,
                    onClick = { selectedOption = DeletePlannedPaymentOption.CURRENT_ONLY }
                )

                Spacer(Modifier.height(16.dp))

                DeleteOptionCard(
                    title = stringResource(R.string.delete_all_future_payments),
                    description = stringResource(R.string.delete_all_future_payments_description),
                    selected = selectedOption == DeletePlannedPaymentOption.ALL_FUTURE,
                    onClick = { selectedOption = DeletePlannedPaymentOption.ALL_FUTURE }
                )
            }
        } else {
            // Show simple description for one-time payments
            Text(
                modifier = Modifier.padding(horizontal = 32.dp),
                text = stringResource(R.string.planned_payment_confirm_deletion_description),
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DeleteOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(
                if (selected) UI.colors.pure else UI.colors.medium,
                UI.shapes.r4
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = UI.typo.b2.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) UI.colors.pureInverse else UI.colors.pureInverse
                )
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = description,
                style = UI.typo.c.style(
                    fontWeight = FontWeight.Medium,
                    color = if (selected) UI.colors.pureInverse else UI.colors.mediumInverse
                )
            )
        }

        Spacer(Modifier.width(12.dp))

        if (selected) {
            IvyIcon(
                icon = R.drawable.ic_check,
                tint = UI.colors.pureInverse
            )
        }
    }
}

@Preview
@Composable
private fun Preview_RecurringPayment() {
    IvyWalletPreview {
        DeletePlannedPaymentModal(
            visible = true,
            isRecurring = true,
            dismiss = { },
            onDelete = { }
        )
    }
}

@Preview
@Composable
private fun Preview_OneTimePayment() {
    IvyWalletPreview {
        DeletePlannedPaymentModal(
            visible = true,
            isRecurring = false,
            dismiss = { },
            onDelete = { }
        )
    }
} 