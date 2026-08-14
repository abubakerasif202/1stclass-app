package au.com.firstclassexpress.driver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.com.firstclassexpress.driver.model.Job
import au.com.firstclassexpress.driver.model.JobStatus
import au.com.firstclassexpress.driver.model.Priority
import au.com.firstclassexpress.driver.viewmodel.AppViewModel

private enum class JobFilterTab(val label: String) {
    ALL("All Jobs"),
    ACTIVE("Active / In Progress"),
    COMPLETED("Completed"),
    EXCEPTIONS("Issues & Exceptions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsListScreen(
    viewModel: AppViewModel,
    onJobClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val allJobs = uiState.jobs
    var selectedTab by remember { mutableStateOf(JobFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredJobs = remember(allJobs, selectedTab, searchQuery) {
        allJobs.filter { job ->
            val matchesTab = when (selectedTab) {
                JobFilterTab.ALL -> true
                JobFilterTab.ACTIVE -> job.status != JobStatus.COMPLETED && !job.status.isTerminal && !job.status.isException
                JobFilterTab.COMPLETED -> job.status == JobStatus.COMPLETED
                JobFilterTab.EXCEPTIONS -> job.status.isException || job.status == JobStatus.ISSUE
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                job.reference.contains(searchQuery, ignoreCase = true) ||
                    job.pickup.companyName.contains(searchQuery, ignoreCase = true) ||
                    job.pickup.suburb.contains(searchQuery, ignoreCase = true) ||
                    job.delivery.companyName.contains(searchQuery, ignoreCase = true) ||
                    job.delivery.suburb.contains(searchQuery, ignoreCase = true) ||
                    job.freightDescription.contains(searchQuery, ignoreCase = true)
            }
            matchesTab && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manifest & Jobs (${allJobs.size})", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by reference, company, suburb…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            )

            // Tab Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(JobFilterTab.entries) { tab ->
                    val count = when (tab) {
                        JobFilterTab.ALL -> allJobs.size
                        JobFilterTab.ACTIVE -> allJobs.count { it.status != JobStatus.COMPLETED && !it.status.isTerminal && !it.status.isException }
                        JobFilterTab.COMPLETED -> allJobs.count { it.status == JobStatus.COMPLETED }
                        JobFilterTab.EXCEPTIONS -> allJobs.count { it.status.isException || it.status == JobStatus.ISSUE }
                    }
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text("${tab.label} ($count)") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No jobs match \"$searchQuery\"" else "No jobs in this category.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredJobs, key = { it.id }) { job ->
                        JobListItem(job = job, onClick = { onJobClick(job.id) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun JobListItem(job: Job, onClick: () -> Unit) {
    val isException = job.status.isException || job.status == JobStatus.ISSUE

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isException) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = job.reference,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (job.priority == Priority.URGENT) {
                        Surface(
                            color = MaterialTheme.colorScheme.error,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "URGENT",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    color = when {
                        job.status == JobStatus.COMPLETED -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        isException -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = job.status.displayLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            job.status == JobStatus.COMPLETED -> Color(0xFF4CAF50)
                            isException -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. PICKUP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = job.pickup.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = job.pickup.suburb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${job.pickupWindowStart} - ${job.pickupWindowEnd}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2. DELIVERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = job.delivery.companyName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = job.delivery.suburb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${job.deliveryWindowStart} - ${job.deliveryWindowEnd}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${job.itemCount} Items · ${job.freightDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (job.isDangerousGoods) {
                    Text(
                        text = "⚠ DG",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
