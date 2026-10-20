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
import mkn.snordy.nexus.ui.NexusMainScreen
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
