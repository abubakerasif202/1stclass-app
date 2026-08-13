package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.domain.sync.SyncStatusSummary

/**
 * The dashboard sync indicator.
 *
 * One line, plain English, no networking jargon. A driver needs to know their work is safe and
 * whether anything needs a human — not what a 503 is.
 */
@Composable
fun SyncStatusRow(
    summary: SyncStatusSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val message = summary.driverMessage()
    val indicator = when (summary) {
        is SyncStatusSummary.Failed -> MaterialTheme.colorScheme.error
        is SyncStatusSummary.AllSynced -> MaterialTheme.colorScheme.primary
        is SyncStatusSummary.Syncing -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = "Sync status: $message" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = indicator
            ) {}
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
