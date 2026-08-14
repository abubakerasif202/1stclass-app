package au.com.firstclassexpress.driver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import au.com.firstclassexpress.driver.domain.model.EvidenceRecord
import au.com.firstclassexpress.driver.domain.model.EvidenceStatus
import au.com.firstclassexpress.driver.domain.model.EvidenceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Driver-facing wording for a piece of evidence. "Saved on device" is never called "synced". */
fun EvidenceStatus.driverLabel(): String = when (this) {
    EvidenceStatus.NONE -> "Not captured"
    EvidenceStatus.PENDING_CAPTURE -> "Not captured"
    EvidenceStatus.SAVED_LOCAL -> "Saved on device"
    EvidenceStatus.PENDING_SYNC -> "Waiting to sync"
    EvidenceStatus.SYNCED -> "Synced"
    EvidenceStatus.FAILED_SYNC -> "Sync failed"
}

fun EvidenceType.driverLabel(): String = when (this) {
    EvidenceType.PICKUP_PHOTO -> "Pickup photo"
    EvidenceType.PICKUP_CONDITION_PHOTO -> "Condition photo"
    EvidenceType.DELIVERY_PHOTO -> "Delivery photo"
    EvidenceType.DAMAGED_FREIGHT_PHOTO -> "Damage photo"
    EvidenceType.CONSIGNMENT_PHOTO -> "Consignment doc"
    EvidenceType.PICKUP_SIGNATURE -> "Pickup signature"
    EvidenceType.DELIVERY_SIGNATURE -> "Delivery signature"
    EvidenceType.DEFECT_PHOTO -> "Defect photo"
    EvidenceType.DOCUMENT -> "Document"
    EvidenceType.OTHER_ATTACHMENT -> "Attachment"
}

/**
 * Horizontal thumbnail strip of the evidence held for a job.
 *
 * Records still awaiting capture are excluded — nothing is shown as proof until a file exists.
 */
@Composable
fun EvidenceGallery(
    records: List<EvidenceRecord>,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No evidence captured yet.",
    onDelete: ((EvidenceRecord) -> Unit)? = null
) {
    val captured = records.filter {
        it.status != EvidenceStatus.PENDING_CAPTURE && it.localUri != null
    }

    if (captured.isEmpty()) {
        Text(
            text = emptyMessage,
            modifier = modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        return
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(captured, key = { it.id }) { record ->
            EvidenceThumbnail(record = record, onDelete = onDelete)
        }
    }
}

@Composable
private fun EvidenceThumbnail(
    record: EvidenceRecord,
    onDelete: ((EvidenceRecord) -> Unit)?
) {
    Card(
        modifier = Modifier.width(160.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box {
            AsyncImage(
                model = record.localUri,
                contentDescription = "${record.type.driverLabel()} thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color.White)
            )
            if (onDelete != null && record.status != EvidenceStatus.SYNCED) {
                IconButton(
                    onClick = { onDelete(record) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete ${record.type.driverLabel()}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = record.type.driverLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            record.signerName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            EvidenceStatusChip(record.status)
            (record.savedAt ?: record.createdAt).let { timestamp ->
                Text(
                    text = TIMESTAMP_FORMAT.format(Date(timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun EvidenceStatusChip(status: EvidenceStatus, modifier: Modifier = Modifier) {
    val container = when (status) {
        EvidenceStatus.SYNCED -> MaterialTheme.colorScheme.primaryContainer
        EvidenceStatus.FAILED_SYNC -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when (status) {
        EvidenceStatus.SYNCED -> MaterialTheme.colorScheme.primary
        EvidenceStatus.FAILED_SYNC -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    AssistChip(
        onClick = {},
        enabled = false,
        modifier = modifier,
        label = {
            Text(
                text = status.driverLabel(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = content
        )
    )
}

private val TIMESTAMP_FORMAT = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
