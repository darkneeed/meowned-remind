package com.meowned.remind.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.meowned.remind.data.model.ForwardMode
import com.meowned.remind.ui.theme.ErrorRed
import com.meowned.remind.ui.theme.SuccessGreen
import com.meowned.remind.ui.theme.TelegramBlue
import com.meowned.remind.ui.theme.WarningOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToTelegram: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onScanQr: () -> Unit
) {
    val context = LocalContext.current
    val isEnabled by viewModel.isEnabled.collectAsState()
    val forwardMode by viewModel.forwardMode.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val chatId by viewModel.chatId.collectAsState()
    val isInitialSetupDone by viewModel.isInitialSetupDone.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val failedCount by viewModel.failedCount.collectAsState()

    var userDismissedSetupDialog by rememberSaveable { mutableStateOf(false) }
    var userRequestedSetupDialog by remember { mutableStateOf(false) }

    val showInitialSetupDialog = (userRequestedSetupDialog || (isInitialSetupDone == false && !isConfigured)) && !userDismissedSetupDialog

    var hasReceiveSms by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasPostNotification by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        hasPostNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    var isBatteryOptimized by remember { mutableStateOf(checkBatteryOptimization(context)) }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                hasPostNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
                isBatteryOptimized = checkBatteryOptimization(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasRequiredPermissions = hasReceiveSms && hasPostNotification

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "meowned ремайнд",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onScanQr) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать QR")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Permission Warning Banner
            if (!hasRequiredPermissions) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Отсутствуют необходимые разрешения",
                                fontWeight = FontWeight.Bold,
                                color = ErrorRed
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Для пересылки SMS требуются разрешения на прием SMS и показ уведомлений службы.",
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val needed = mutableListOf<String>()
                                if (!hasReceiveSms) needed.add(Manifest.permission.RECEIVE_SMS)
                                if (!hasPostNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(needed.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Text("Предоставить разрешения")
                        }
                    }
                }
            }

            // QR Quick Connect Card (Top priority)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TelegramBlue.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = TelegramBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Быстрое подключение",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TelegramBlue
                        )
                        Text(
                            text = "Отсканируйте QR-код для настройки сервера или бота",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onScanQr,
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("QR-код", fontSize = 13.sp)
                    }
                }
            }

            // Master Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled && hasRequiredPermissions) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isEnabled && hasRequiredPermissions) "Служба активна" else "Служба выключена",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isEnabled && hasRequiredPermissions) SuccessGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isEnabled && hasRequiredPermissions) {
                                if (forwardMode == ForwardMode.WITH_SERVER) "Входящие SMS пересылаются на сервер" else "Входящие SMS пересылаются в Telegram"
                            } else {
                                "Пересылка SMS приостановлена"
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isEnabled && hasRequiredPermissions,
                        enabled = hasRequiredPermissions,
                        onCheckedChange = { checked ->
                            if (checked && !isConfigured) {
                                userDismissedSetupDialog = false
                                userRequestedSetupDialog = true
                            } else {
                                viewModel.toggleService(checked)
                            }
                        }
                    )
                }
            }

            // Initial Setup Prompt if not configured
            if (!isConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = WarningOrange.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = WarningOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Начальная настройка не завершена",
                                fontWeight = FontWeight.Bold,
                                color = WarningOrange
                            )
                        }
                        Text(
                            text = "Выберите режим работы для пересылки сообщений:",
                            fontSize = 13.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setForwardMode(ForwardMode.WITH_SERVER)
                                    viewModel.setInitialSetupDone(true)
                                    onNavigateToTelegram()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("С сервером", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    viewModel.setForwardMode(ForwardMode.WITHOUT_SERVER)
                                    viewModel.setInitialSetupDone(true)
                                    onNavigateToTelegram()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Без сервера", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else {
                // Active configuration overview card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (forwardMode == ForwardMode.WITH_SERVER) Icons.Default.Dns else Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (forwardMode == ForwardMode.WITH_SERVER) MaterialTheme.colorScheme.primary else TelegramBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (forwardMode == ForwardMode.WITH_SERVER) "Режим: С сервером" else "Режим: Без сервера (Telegram)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (forwardMode == ForwardMode.WITH_SERVER) {
                                    if (serverUrl.isNotBlank()) serverUrl else "URL не указан"
                                } else {
                                    if (chatId.isNotBlank()) "Chat ID: $chatId" else "Chat ID не указан"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = onNavigateToTelegram) {
                            Text("Изменить")
                        }
                    }
                }
            }

            // Battery Optimization Banner
            if (isBatteryOptimized) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = WarningOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Фоновая оптимизация включена",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Android может приостановить работу приложения в спящем режиме. Рекомендуется отключить оптимизацию батареи.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                requestIgnoreBatteryOptimization(context)
                                isBatteryOptimized = checkBatteryOptimization(context)
                            }
                        ) {
                            Text("Отключить ограничения батареи")
                        }
                    }
                }
            }

            // Stats Section
            Text(
                text = "Статистика",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Переслано", fontSize = 13.sp, color = SuccessGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$successCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = SuccessGreen
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Ошибок", fontSize = 13.sp, color = ErrorRed)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$failedCount",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = ErrorRed
                        )
                    }
                }
            }

            // Quick Actions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Быстрые действия", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Button(
                        onClick = { viewModel.sendMockSms() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сэмулировать входящее SMS")
                    }

                    OutlinedButton(
                        onClick = onNavigateToLogs,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Открыть историю сообщений")
                    }
                }
            }
        }
    }

    // Initial Setup Dialog
    if (showInitialSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                userDismissedSetupDialog = true
                userRequestedSetupDialog = false
            },
            title = { Text("Начальная настройка", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Выберите режим работы для пересылки входящих SMS:",
                        fontSize = 14.sp
                    )

                    // Option 1: With Server
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setForwardMode(ForwardMode.WITH_SERVER)
                                viewModel.setInitialSetupDone(true)
                                userRequestedSetupDialog = false
                                onNavigateToTelegram()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "С сервером",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Пересылка SMS на ваш Webhook / REST API сервер в формате JSON",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Without Server (Telegram)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setForwardMode(ForwardMode.WITHOUT_SERVER)
                                viewModel.setInitialSetupDone(true)
                                userRequestedSetupDialog = false
                                onNavigateToTelegram()
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = TelegramBlue,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Без сервера",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = TelegramBlue
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Прямая отправка в Telegram через Bot API без сторонних серверов",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    userDismissedSetupDialog = true
                    userRequestedSetupDialog = false
                }) {
                    Text("Позже")
                }
            }
        )
    }
}

private fun checkBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    return powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun requestIgnoreBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
