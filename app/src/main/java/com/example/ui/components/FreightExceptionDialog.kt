package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.FreightExceptionReason
import androidx.compose.foundation.layout.Row

/**
 * Reason + notes capture for a freight exception.
 *
 * The dialog surfaces what each reason demands, but the authoritative check is
 * [com.example.domain.rules.FreightExceptionRules], re-run by the ViewModel on save.
 */
@Composable
fun FreightExceptionDialog(
    hasSavedPhoto: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAddPhoto: () -> Unit,
    onSave: (FreightExceptionReason, String) -> Unit
) {
    var reason by remember { mutableStateOf(FreightExceptionReason.DAMAGED) }
    var notes by remember { mutableStateOf("") }

    val needsPhoto = reason.requiresPhoto && !hasSavedPhoto
    val canSave = notes.isNotBlank() && !needsPhoto

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record freight exception") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "What went wrong?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                FreightExceptionReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = reason == option,
                                onClick = { reason = option }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4
                )

                if (needsPhoto) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Damage must be photographed before it can be recorded.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedActionButton(text = "Add damage photo", onClick = onAddPhoto)
                }

                errorMessage?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(reason, notes) },
                enabled = canSave
            ) { Text("Save exception") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Compact summary row used to show recorded exceptions inside a workflow. */
@Composable
fun FreightExceptionSummary(
    reasonLabel: String,
    notes: String,
    resolved: Boolean,
    onResolve: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = if (resolved) "$reasonLabel — resolved" else "$reasonLabel — open",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (resolved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
        Text(text = notes, style = MaterialTheme.typography.bodyMedium)
        if (!resolved && onResolve != null) {
            TextButton(onClick = onResolve) { Text("Mark resolved") }
        }
    }
}
