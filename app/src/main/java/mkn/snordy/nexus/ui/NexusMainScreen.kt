package mkn.snordy.nexus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mkn.snordy.nexus.viewModel.NexusViewModel

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