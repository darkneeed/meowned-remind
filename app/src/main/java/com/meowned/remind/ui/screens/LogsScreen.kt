package com.meowned.remind.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowned.remind.data.db.SmsLogEntity
import com.meowned.remind.data.model.ForwardStatus
import com.meowned.remind.ui.theme.ErrorRed
import com.meowned.remind.ui.theme.SuccessGreen
import com.meowned.remind.ui.theme.WarningOrange
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    val selectedFilter by viewModel.logFilterStatus.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("История пересылки", fontWeight = FontWeight.Bold) },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Очистить историю")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { viewModel.setLogFilterStatus(null) },
                    label = { Text("Все (${logs.size})") }
                )
                FilterChip(
                    selected = selectedFilter == ForwardStatus.SUCCESS,
                    onClick = { viewModel.setLogFilterStatus(ForwardStatus.SUCCESS) },
                    label = { Text("Успешно") }
                )
                FilterChip(
                    selected = selectedFilter == ForwardStatus.FAILED,
                    onClick = { viewModel.setLogFilterStatus(ForwardStatus.FAILED) },
                    label = { Text("Ошибки") }
                )
                FilterChip(
                    selected = selectedFilter == ForwardStatus.SKIPPED_BY_FILTER,
                    onClick = { viewModel.setLogFilterStatus(ForwardStatus.SKIPPED_BY_FILTER) },
                    label = { Text("Фильтр") }
                )
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Inbox,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Сообщений пока нет",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LogItemCard(
                            log = log,
                            onRetry = { viewModel.retryLog(log.id) }
                        )
                    }
                }
            }
        }
    }

    // Clear Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все записи журналов пересылки будут безвозвратно удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearLogs()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun LogItemCard(log: SmsLogEntity, onRetry: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }
    val dateStr = remember(log.timestamp) {
        formatter.format(Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Sender + Status Badge + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = log.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SuggestionChip(
                        onClick = {},
                        label = { Text("SIM ${log.simSlot}", fontSize = 10.sp) },
                        modifier = Modifier.height(24.dp)
                    )
                }

                StatusBadge(status = log.status)
            }

            // Time
            Text(
                text = dateStr,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Message Body
            Text(
                text = log.body,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2
            )

            // Error info if failed
            if (log.status == ForwardStatus.FAILED && !log.errorMessage.isNullOrBlank()) {
                Text(
                    text = "Причина ошибки: ${log.errorMessage}",
                    fontSize = 11.sp,
                    color = ErrorRed
                )
            }

            if (log.status == ForwardStatus.SKIPPED_BY_FILTER && !log.errorMessage.isNullOrBlank()) {
                Text(
                    text = "Фильтр: ${log.errorMessage}",
                    fontSize = 11.sp,
                    color = WarningOrange
                )
            }

            // Retry action if failed
            if (log.status == ForwardStatus.FAILED) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Повторить отправку")
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: ForwardStatus) {
    val (text, color) = when (status) {
        ForwardStatus.SUCCESS -> "Успешно" to SuccessGreen
        ForwardStatus.FAILED -> "Ошибка" to ErrorRed
        ForwardStatus.SKIPPED_BY_FILTER -> "Пропущено" to WarningOrange
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
