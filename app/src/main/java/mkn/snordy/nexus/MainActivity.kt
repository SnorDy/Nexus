package mkn.snordy.nexus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import mkn.snordy.nexus.ui.theme.NexusTheme
import mkn.snordy.nexus.viewModel.AuthState
import mkn.snordy.nexus.viewModel.AuthViewModel
import mkn.snordy.nexus.viewModel.NexusViewModel

data class NexusUiState(
    val isStopped: Boolean = false,
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val isExecuting: Boolean = false,
    val error: String? =null
)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NexusMainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}


@Composable
fun NexusMainScreen(
    modifier: Modifier = Modifier,
    viewModel: NexusViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (micGranted) {
            viewModel.startService(context)
        }
    }
    val checkPermissionsAndStart = {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            viewModel.startService(context)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            uiState.isStopped -> {
                Button(onClick = checkPermissionsAndStart) {
                    Text("Запустить Nexus")
                }
            }

            uiState.isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Загрузка нейросетей...")
            }
            uiState.error != null -> {
                Text(text = "Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                Button(onClick = { viewModel.stopService(context) }) {
                    Text("Restart")
                }
            }

            uiState.isListening -> {
                Text(
                    text = if (uiState.isExecuting) "Nexus выполняет..." else "Nexus слушает...",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.stopService(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Остановить")
                }
            }
        }
    }
}


@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    val sdk = remember { YandexAuthSdk.create(YandexAuthOptions(context.applicationContext)) }

    val launcher = rememberLauncherForActivityResult(sdk.contract) { result ->
        when (result) {
            is YandexAuthResult.Success -> viewModel.handleYandexResult(result.token.value)
            is YandexAuthResult.Failure -> viewModel.handleError(result.exception)
            YandexAuthResult.Cancelled -> viewModel.resetState()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        when (val state = authState) {
            is AuthState.Idle -> {
                Row(Modifier.fillMaxWidth()){
                Button(onClick = { launcher.launch(YandexAuthLoginOptions()) }) {
                    Text("Войти через Яндекс")
                }}

            }

            is AuthState.Loading -> CircularProgressIndicator()

            is AuthState.Success -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Привет, ${state.userName}!",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    state.email?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyLarge)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.resetState() }) {
                        Text("Выйти")
                    }
                }
            }

            is AuthState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetState() },
                    title = { Text("Ошибка входа") },
                    text = { Text(state.message) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.resetState() }) {
                            Text("ОК")
                        }
                    }
                )
            }
        }
    }
}