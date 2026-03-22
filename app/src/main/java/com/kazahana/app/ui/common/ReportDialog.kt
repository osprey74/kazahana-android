package com.kazahana.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kazahana.app.R

enum class ReportReason(val apiValue: String) {
    SPAM("reasonSpam"),
    VIOLATION("reasonViolation"),
    MISLEADING("reasonMisleading"),
    SEXUAL("reasonSexual"),
    RUDE("reasonRude"),
    OTHER("reasonOther"),
}

@Composable
fun ReportDialog(
    isPost: Boolean,
    isSubmitting: Boolean = false,
    onSubmit: (reasonType: String, details: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPost) stringResource(R.string.report_post)
                else stringResource(R.string.report_user),
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.report_reason),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))

                ReportReason.entries.forEach { reason ->
                    val label = when (reason) {
                        ReportReason.SPAM -> stringResource(R.string.report_reason_spam)
                        ReportReason.VIOLATION -> stringResource(R.string.report_reason_violation)
                        ReportReason.MISLEADING -> stringResource(R.string.report_reason_misleading)
                        ReportReason.SEXUAL -> stringResource(R.string.report_reason_sexual)
                        ReportReason.RUDE -> stringResource(R.string.report_reason_rude)
                        ReportReason.OTHER -> stringResource(R.string.report_reason_other)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason },
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    placeholder = { Text(stringResource(R.string.report_details_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedReason?.let { reason ->
                        onSubmit(reason.apiValue, details)
                    }
                },
                enabled = selectedReason != null && !isSubmitting,
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.report_submit))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
