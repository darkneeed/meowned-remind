package com.meowned.remind.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowned.remind.data.model.ForwardMode
import com.meowned.remind.data.preferences.AppPreferences
import com.meowned.remind.ui.theme.ErrorRed
import com.meowned.remind.ui.theme.SuccessGreen
import com.meowned.remind.ui.theme.TelegramBlue
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramConfigScreen(
    viewModel: MainViewModel,
    onScanQr: () -> Unit
) {
    val forwardMode by viewModel.forwardMode.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverApiKey by viewModel.serverApiKey.collectAsState()
    val serverDeviceName by viewModel.serverDeviceName.collectAsState()
    val serverTestState by viewModel.serverTestState.collectAsState()

    val botToken by viewModel.botToken.collectAsState()
    val chatId by viewModel.chatId.collectAsState()
    val topicId by viewModel.topicId.collectAsState()
    val template by viewModel.template.collectAsState()
    val testState by viewModel.testState.collectAsState()

    // Local states for Server mode
    var localServerUrl by remember { mutableStateOf(serverUrl) }
    var localServerApiKey by remember { mutableStateOf(serverApiKey) }
    var localServerDeviceName by remember { mutableStateOf(serverDeviceName) }

    var lastSavedServerUrl by remember { mutableStateOf(serverUrl) }
    var lastSavedServerApiKey by remember { mutableStateOf(serverApiKey) }
    var lastSavedServerDeviceName by remember { mutableStateOf(serverDeviceName) }

    // Local states for Telegram mode
    var localBotToken by remember { mutableStateOf(botToken) }
    var localChatId by remember { mutableStateOf(chatId) }
    var localTopicId by remember { mutableStateOf(topicId) }
    var localTemplate by remember { mutableStateOf(template) }

    var lastSavedBotToken by remember { mutableStateOf(botToken) }
    var lastSavedChatId by remember { mutableStateOf(chatId) }
    var lastSavedTopicId by remember { mutableStateOf(topicId) }
    var lastSavedTemplate by remember { mutableStateOf(template) }

    var showTokenPassword by remember { mutableStateOf(false) }
    var showApiKeyPassword by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Sync external changes for Server mode
    LaunchedEffect(serverUrl) {
        if (serverUrl != lastSavedServerUrl) {
            localServerUrl = serverUrl
            lastSavedServerUrl = serverUrl
        }
    }
    LaunchedEffect(serverApiKey) {
        if (serverApiKey != lastSavedServerApiKey) {
            localServerApiKey = serverApiKey
            lastSavedServerApiKey = serverApiKey
        }
    }
    LaunchedEffect(serverDeviceName) {
        if (serverDeviceName != lastSavedServerDeviceName) {
            localServerDeviceName = serverDeviceName
            lastSavedServerDeviceName = serverDeviceName
        }
    }

    // Debounced autosave for Server mode
    LaunchedEffect(localServerUrl) {
        if (localServerUrl != serverUrl) {
            delay(400)
            lastSavedServerUrl = localServerUrl
            viewModel.setServerUrl(localServerUrl)
        }
    }
    LaunchedEffect(localServerApiKey) {
        if (localServerApiKey != serverApiKey) {
            delay(400)
            lastSavedServerApiKey = localServerApiKey
            viewModel.setServerApiKey(localServerApiKey)
        }
    }
    LaunchedEffect(localServerDeviceName) {
        if (localServerDeviceName != serverDeviceName) {
            delay(400)
            lastSavedServerDeviceName = localServerDeviceName
            viewModel.setServerDeviceName(localServerDeviceName)
        }
    }

    // Sync external changes for Telegram mode
    LaunchedEffect(botToken) {
        if (botToken != lastSavedBotToken) {
            localBotToken = botToken
            lastSavedBotToken = botToken
        }
    }
    LaunchedEffect(chatId) {
        if (chatId != lastSavedChatId) {
            localChatId = chatId
            lastSavedChatId = chatId
        }
    }
    LaunchedEffect(topicId) {
        if (topicId != lastSavedTopicId) {
            localTopicId = topicId
            lastSavedTopicId = topicId
        }
    }
    LaunchedEffect(template) {
        if (template != lastSavedTemplate) {
            localTemplate = template
            lastSavedTemplate = template
        }
    }

    // Debounced autosave for Telegram mode
    LaunchedEffect(localBotToken) {
        if (localBotToken != botToken) {
            delay(400)
            lastSavedBotToken = localBotToken
            viewModel.setBotToken(localBotToken)
        }
    }
    LaunchedEffect(localChatId) {
        if (localChatId != chatId) {
            delay(400)
            lastSavedChatId = localChatId
            viewModel.setChatId(localChatId)
        }
    }
    LaunchedEffect(localTopicId) {
        if (localTopicId != topicId) {
            delay(400)
            lastSavedTopicId = localTopicId
            viewModel.setTopicId(localTopicId)
        }
    }
    LaunchedEffect(localTemplate) {
        if (localTemplate != template) {
            delay(400)
            lastSavedTemplate = localTemplate
            viewModel.setTemplate(localTemplate)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (forwardMode == ForwardMode.WITH_SERVER) {
                viewModel.setServerUrl(localServerUrl)
                viewModel.setServerApiKey(localServerApiKey)
                viewModel.setServerDeviceName(localServerDeviceName)
            } else {
                viewModel.setBotToken(localBotToken)
                viewModel.setChatId(localChatId)
                viewModel.setTopicId(localTopicId)
                viewModel.setTemplate(localTemplate)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки пересылки", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onScanQr) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать QR")
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Инструкция")
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
            // Mode Selector Tabs
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Режим пересылки сообщений",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    TabRow(
                        selectedTabIndex = if (forwardMode == ForwardMode.WITH_SERVER) 0 else 1,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Tab(
                            selected = forwardMode == ForwardMode.WITH_SERVER,
                            onClick = {
                                viewModel.setBotToken(localBotToken)
                                viewModel.setChatId(localChatId)
                                viewModel.setTopicId(localTopicId)
                                viewModel.setTemplate(localTemplate)
                                viewModel.setForwardMode(ForwardMode.WITH_SERVER)
                                viewModel.setInitialSetupDone(true)
                            },
                            text = { Text("С сервером", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.Default.Dns, contentDescription = null) }
                        )
                        Tab(
                            selected = forwardMode == ForwardMode.WITHOUT_SERVER,
                            onClick = {
                                viewModel.setServerUrl(localServerUrl)
                                viewModel.setServerApiKey(localServerApiKey)
                                viewModel.setServerDeviceName(localServerDeviceName)
                                viewModel.setForwardMode(ForwardMode.WITHOUT_SERVER)
                                viewModel.setInitialSetupDone(true)
                            },
                            text = { Text("Без сервера", fontWeight = FontWeight.SemiBold) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
                        )
                    }
                }
            }

            if (forwardMode == ForwardMode.WITH_SERVER) {
                // ==========================================
                // WITH SERVER MODE
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "В режиме 'С сервером' входящие SMS пересылаются HTTP POST запросом в формате JSON на ваш Webhook или REST API.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // QR Scan Quick Button
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сканировать QR-код сервера", fontWeight = FontWeight.SemiBold)
                }

                // Server URL Field
                OutlinedTextField(
                    value = localServerUrl,
                    onValueChange = { localServerUrl = it },
                    label = { Text("URL сервера (Webhook / REST API)") },
                    placeholder = { Text("https://my-server.com/api/sms") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    supportingText = {
                        Text("Полный адрес endpoint для приема POST запросов")
                    }
                )

                // API Key / Secret Token Field
                OutlinedTextField(
                    value = localServerApiKey,
                    onValueChange = { localServerApiKey = it },
                    label = { Text("API ключ / Секретный токен (Опционально)") },
                    placeholder = { Text("secret_token_123") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showApiKeyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKeyPassword = !showApiKeyPassword }) {
                            Icon(
                                imageVector = if (showApiKeyPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    supportingText = {
                        Text("Будет передан в заголовках Authorization: Bearer и X-API-Key")
                    }
                )

                // Device Name Field
                OutlinedTextField(
                    value = localServerDeviceName,
                    onValueChange = { localServerDeviceName = it },
                    label = { Text("Имя устройства (Опционально)") },
                    placeholder = { Text("Телефон 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = {
                        Text("Передается в поле device_name для идентификации источника")
                    }
                )

                // JSON Payload Schema Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Формат отправляемого JSON", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = "{\n" +
                                    "  \"event\": \"sms_received\",\n" +
                                    "  \"sender\": \"+79991234567\",\n" +
                                    "  \"body\": \"Код: 1234\",\n" +
                                    "  \"timestamp\": 1700000000000,\n" +
                                    "  \"sim_slot\": 1,\n" +
                                    "  \"device_name\": \"Pixel 7\",\n" +
                                    "  \"text\": \"Шаблон сообщения...\"\n" +
                                    "}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Test Server Button
                Button(
                    onClick = {
                        lastSavedServerUrl = localServerUrl
                        lastSavedServerApiKey = localServerApiKey
                        lastSavedServerDeviceName = localServerDeviceName
                        viewModel.testServer(localServerUrl, localServerApiKey, localServerDeviceName)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !serverTestState.isLoading && localServerUrl.isNotBlank()
                ) {
                    if (serverTestState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверка соединения...")
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверить подключение к серверу")
                    }
                }

                // Server Test Result Banner
                if (serverTestState.resultMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (serverTestState.isSuccess) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (serverTestState.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (serverTestState.isSuccess) SuccessGreen else ErrorRed
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = serverTestState.resultMessage ?: "",
                                fontSize = 13.sp,
                                color = if (serverTestState.isSuccess) SuccessGreen else ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.resetServerTestState() }) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

            } else {
                // ==========================================
                // WITHOUT SERVER MODE (TELEGRAM DIRECT)
                // ==========================================
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TelegramBlue.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = TelegramBlue,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "В режиме 'Без сервера' сообщения отправляются напрямую в Telegram через Bot API без участия сторонних серверов.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // QR Scan Quick Button
                OutlinedButton(
                    onClick = onScanQr,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TelegramBlue)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сканировать QR-код из бота", fontWeight = FontWeight.SemiBold)
                }

                // Telegram Bot Token Field
                OutlinedTextField(
                    value = localBotToken,
                    onValueChange = { localBotToken = it },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456789:AAF_abcdef...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showTokenPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showTokenPassword = !showTokenPassword }) {
                            Icon(
                                imageVector = if (showTokenPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    supportingText = {
                        Text("Токен вашего бота, полученный от @BotFather")
                    }
                )

                // Chat ID Field
                OutlinedTextField(
                    value = localChatId,
                    onValueChange = { localChatId = it },
                    label = { Text("Chat ID") },
                    placeholder = { Text("123456789 или -100123456789") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text("ID вашего аккаунта, группы или канала (узнать можно через @userinfobot)")
                    }
                )

                // Topic / Thread ID Field (Optional)
                OutlinedTextField(
                    value = localTopicId,
                    onValueChange = { localTopicId = it },
                    label = { Text("Topic ID (Опционально)") },
                    placeholder = { Text("123 (для форумов/тем в группе)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text("Оставьте пустым, если пересылка идет в обычный чат или ЛС")
                    }
                )

                // Template Editor Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Шаблон сообщения (HTML)", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                        Text(text = "Нажмите для добавления тегов:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val placeholders = listOf("{sender}", "{body}", "{time}", "{date}", "{datetime}", "{sim}")
                            placeholders.forEach { tag ->
                                AssistChip(
                                    onClick = { localTemplate = localTemplate + " " + tag },
                                    label = { Text(tag, fontSize = 12.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = localTemplate,
                            onValueChange = { localTemplate = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { localTemplate = AppPreferences.DEFAULT_TEMPLATE }) {
                                Text("Сбросить по умолчанию")
                            }
                        }
                    }
                }

                // Test Telegram Button
                Button(
                    onClick = {
                        lastSavedBotToken = localBotToken
                        lastSavedChatId = localChatId
                        lastSavedTopicId = localTopicId
                        viewModel.testTelegram(localBotToken, localChatId, localTopicId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !testState.isLoading && localBotToken.isNotBlank() && localChatId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                ) {
                    if (testState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправка теста...")
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверить подключение (Тест)")
                    }
                }

                // Telegram Test Result Banner
                if (testState.resultMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (testState.isSuccess) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (testState.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (testState.isSuccess) SuccessGreen else ErrorRed
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = testState.resultMessage ?: "",
                                fontSize = 13.sp,
                                color = if (testState.isSuccess) SuccessGreen else ErrorRed,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.resetTestState() }) {
                                Icon(Icons.Default.Close, contentDescription = "Закрыть", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(if (forwardMode == ForwardMode.WITH_SERVER) "Настройка режима 'С сервером'" else "Настройка режима 'Без сервера'")
            },
            text = {
                if (forwardMode == ForwardMode.WITH_SERVER) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Разверните веб-сервер с поддержкой приема HTTP POST запросов.")
                        Text("2. Создайте endpoint (например, https://example.com/api/sms) для приема JSON.")
                        Text("3. Вставьте URL в поле 'URL сервера'.")
                        Text("4. При необходимости укажите секретный токен в поле 'API ключ'.")
                        Text("5. Нажмите кнопку 'Проверить подключение к серверу'.")
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("1. Откройте Telegram и найдите бота @BotFather.")
                        Text("2. Отправьте команду /newbot и следуйте инструкциям для создания бота.")
                        Text("3. Скопируйте полученный HTTP API Token и вставьте в поле 'Bot Token'.")
                        Text("4. Найдите своего бота в Telegram и обязательно нажмите /start.")
                        Text("5. Откройте бота @userinfobot, чтобы узнать свой числовой Chat ID, и вставьте его.")
                        Text("6. Нажмите 'Проверить подключение'.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Понятно")
                }
            }
        )
    }
}
