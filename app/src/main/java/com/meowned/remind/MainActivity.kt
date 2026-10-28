package com.meowned.remind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.meowned.remind.ui.screens.*
import com.meowned.remind.ui.theme.MeownedRemindTheme

import com.meowned.remind.data.model.ForwardMode

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Главная", Icons.Default.Home)
    object Config : Screen("telegram", "Подключение", Icons.Default.CloudSync)
    object Rules : Screen("rules", "Правила", Icons.Default.FilterList)
    object Logs : Screen("logs", "История", Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.processQrCode(result.contents) { _, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()
        handleIntent(intent)

        setContent {
            MeownedRemindTheme {
                MainApp(
                    viewModel = viewModel,
                    onScanQr = { launchQrScanner() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString
        if (!data.isNullOrBlank() && (data.startsWith("meowned-remind://") || data.startsWith("meowned://") || data.startsWith("https://") || data.startsWith("http://"))) {
            viewModel.processQrCode(data) { _, msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setPrompt("Наведите камеру на QR-код для настройки")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setBarcodeImageEnabled(false)
        }
        qrScanLauncher.launch(options)
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val ungranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungranted.isNotEmpty()) {
            permissionLauncher.launch(ungranted.toTypedArray())
        }
    }
}

@Composable
fun MainApp(
    viewModel: MainViewModel,
    onScanQr: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        Screen.Home,
        Screen.Config,
        Screen.Rules,
        Screen.Logs
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToTelegram = {
                        navController.navigate(Screen.Config.route)
                    },
                    onNavigateToLogs = {
                        navController.navigate(Screen.Logs.route)
                    },
                    onScanQr = onScanQr
                )
            }
            composable(Screen.Config.route) {
                TelegramConfigScreen(
                    viewModel = viewModel,
                    onScanQr = onScanQr
                )
            }
            composable(Screen.Rules.route) {
                RulesScreen(viewModel = viewModel)
            }
            composable(Screen.Logs.route) {
                LogsScreen(viewModel = viewModel)
            }
        }
    }

    val pendingSetup by viewModel.pendingSetupData.collectAsState()
    val context = LocalContext.current
    if (pendingSetup != null) {
        val setup = pendingSetup!!
        AlertDialog(
            onDismissRequest = { viewModel.dismissSetup() },
            title = { Text("Подтверждение подключения") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Стороннее приложение или ссылка запрашивает настройку пересылки ваших входящих SMS. Применить параметры?")
                    Spacer(modifier = Modifier.height(4.dp))
                    if (setup.mode == ForwardMode.WITH_SERVER) {
                        Text("Режим: С сервером", fontWeight = FontWeight.Bold)
                        Text("URL сервера: ${setup.serverUrl}", fontWeight = FontWeight.SemiBold)
                        if (setup.serverApiKey.isNotBlank()) {
                            val maskedKey = if (setup.serverApiKey.length > 6) {
                                setup.serverApiKey.take(3) + "***" + setup.serverApiKey.takeLast(3)
                            } else {
                                "***"
                            }
                            Text("API ключ: $maskedKey", fontWeight = FontWeight.SemiBold)
                        }
                        if (setup.deviceName.isNotBlank()) {
                            Text("Устройство: ${setup.deviceName}", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val maskedToken = if (setup.token.length > 8) {
                            setup.token.take(5) + "***" + setup.token.takeLast(3)
                        } else {
                            "***"
                        }
                        Text("Режим: Без сервера (Telegram)", fontWeight = FontWeight.Bold)
                        Text("Chat ID: ${setup.chatId}", fontWeight = FontWeight.SemiBold)
                        Text("Bot Token: $maskedToken", fontWeight = FontWeight.SemiBold)
                        if (setup.topicId.isNotBlank()) {
                            Text("Topic ID: ${setup.topicId}", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmSetup { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                ) {
                    Text("Подтвердить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissSetup() }) {
                    Text("Отмена")
                }
            }
        )
    }
}
