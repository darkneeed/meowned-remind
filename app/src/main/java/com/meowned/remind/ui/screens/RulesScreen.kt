package com.meowned.remind.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meowned.remind.data.model.FilterMode
import com.meowned.remind.data.model.SimSlotFilter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: MainViewModel) {
    val filterMode by viewModel.filterMode.collectAsState()
    val senderRules by viewModel.senderRules.collectAsState()
    val keywordWhitelist by viewModel.keywordWhitelist.collectAsState()
    val keywordBlacklist by viewModel.keywordBlacklist.collectAsState()
    val simSlotFilter by viewModel.simSlotFilter.collectAsState()

    var localSenderRules by remember { mutableStateOf(senderRules) }
    var localKwWhitelist by remember { mutableStateOf(keywordWhitelist) }
    var localKwBlacklist by remember { mutableStateOf(keywordBlacklist) }

    var lastSavedSenderRules by remember { mutableStateOf(senderRules) }
    var lastSavedKwWhitelist by remember { mutableStateOf(keywordWhitelist) }
    var lastSavedKwBlacklist by remember { mutableStateOf(keywordBlacklist) }

    LaunchedEffect(senderRules) {
        if (senderRules != lastSavedSenderRules) {
            localSenderRules = senderRules
            lastSavedSenderRules = senderRules
        }
    }

    LaunchedEffect(keywordWhitelist) {
        if (keywordWhitelist != lastSavedKwWhitelist) {
            localKwWhitelist = keywordWhitelist
            lastSavedKwWhitelist = keywordWhitelist
        }
    }

    LaunchedEffect(keywordBlacklist) {
        if (keywordBlacklist != lastSavedKwBlacklist) {
            localKwBlacklist = keywordBlacklist
            lastSavedKwBlacklist = keywordBlacklist
        }
    }

    LaunchedEffect(localSenderRules) {
        if (localSenderRules != senderRules) {
            delay(400)
            lastSavedSenderRules = localSenderRules
            viewModel.setSenderRules(localSenderRules)
        }
    }

    LaunchedEffect(localKwWhitelist) {
        if (localKwWhitelist != keywordWhitelist) {
            delay(400)
            lastSavedKwWhitelist = localKwWhitelist
            viewModel.setKeywordWhitelist(localKwWhitelist)
        }
    }

    LaunchedEffect(localKwBlacklist) {
        if (localKwBlacklist != keywordBlacklist) {
            delay(400)
            lastSavedKwBlacklist = localKwBlacklist
            viewModel.setKeywordBlacklist(localKwBlacklist)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фильтры и правила", fontWeight = FontWeight.Bold) },
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
            // SIM Card Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Выбор SIM-карты (Dual SIM)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        text = "С каких SIM-карт пересылать входящие SMS:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = simSlotFilter == SimSlotFilter.ALL,
                            onClick = { viewModel.setSimSlotFilter(SimSlotFilter.ALL) },
                            label = { Text("Все SIM") }
                        )
                        FilterChip(
                            selected = simSlotFilter == SimSlotFilter.SIM_1,
                            onClick = { viewModel.setSimSlotFilter(SimSlotFilter.SIM_1) },
                            label = { Text("Только SIM 1") }
                        )
                        FilterChip(
                            selected = simSlotFilter == SimSlotFilter.SIM_2,
                            onClick = { viewModel.setSimSlotFilter(SimSlotFilter.SIM_2) },
                            label = { Text("Только SIM 2") }
                        )
                    }
                }
            }

            // Sender Filter Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Фильтрация отправителей", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterMode == FilterMode.ALLOW_ALL,
                            onClick = { viewModel.setFilterMode(FilterMode.ALLOW_ALL) },
                            label = { Text("Все") }
                        )
                        FilterChip(
                            selected = filterMode == FilterMode.WHITELIST,
                            onClick = { viewModel.setFilterMode(FilterMode.WHITELIST) },
                            label = { Text("Белый список") }
                        )
                        FilterChip(
                            selected = filterMode == FilterMode.BLACKLIST,
                            onClick = { viewModel.setFilterMode(FilterMode.BLACKLIST) },
                            label = { Text("Черный список") }
                        )
                    }

                    if (filterMode != FilterMode.ALLOW_ALL) {
                        OutlinedTextField(
                            value = localSenderRules,
                            onValueChange = { localSenderRules = it },
                            label = {
                                Text(if (filterMode == FilterMode.WHITELIST) "Разрешенные отправители" else "Заблокированные отправители")
                            },
                            placeholder = { Text("900, Tinkoff, +79991234567, ^[0-9]{3}$") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            supportingText = {
                                Text("Укажите номера или имена через запятую/новую строку. Поддерживаются регулярные выражения.")
                            }
                        )
                    }
                }
            }

            // Keyword Filters Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Фильтры по тексту сообщения", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    // Keyword Whitelist
                    OutlinedTextField(
                        value = localKwWhitelist,
                        onValueChange = { localKwWhitelist = it },
                        label = { Text("Только если содержит слова (Белый список)") },
                        placeholder = { Text("код, пароль, подтвержд, auth, code") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            Text("Пересылать только SMS, содержащие хотя бы одно из этих слов (оставьте пустым для отключения)")
                        }
                    )

                    // Keyword Blacklist
                    OutlinedTextField(
                        value = localKwBlacklist,
                        onValueChange = { localKwBlacklist = it },
                        label = { Text("Игнорировать при наличии слов (Черный список)") },
                        placeholder = { Text("скидка, акция, такси, займ, одобрен") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            Text("Не пересылать SMS, если в них встречаются спам-слова")
                        }
                    )
                }
            }
        }
    }
}
