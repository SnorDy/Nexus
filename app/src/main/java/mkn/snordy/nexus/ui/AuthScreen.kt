package mkn.snordy.nexus.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yandex.authsdk.YandexAuthLoginOptions
import com.yandex.authsdk.YandexAuthOptions
import com.yandex.authsdk.YandexAuthResult
import com.yandex.authsdk.YandexAuthSdk
import mkn.snordy.nexus.viewModel.AuthState
import mkn.snordy.nexus.viewModel.AuthViewModel

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