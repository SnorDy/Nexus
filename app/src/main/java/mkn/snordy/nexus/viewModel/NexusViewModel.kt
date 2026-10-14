package mkn.snordy.nexus.viewModel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mkn.snordy.nexus.NexusListenService
import mkn.snordy.nexus.NexusState
import mkn.snordy.nexus.NexusStatus
import mkn.snordy.nexus.NexusUiState

class NexusViewModel : ViewModel() {
    val uiState: StateFlow<NexusUiState> = NexusState.status
        .map { status ->
            when (status) {
                NexusStatus.Stopped -> NexusUiState(isStopped = true)
                NexusStatus.Loading -> NexusUiState(isLoading = true)
                NexusStatus.Listening -> NexusUiState(isListening = true)
                NexusStatus.WakeDetected -> NexusUiState(isListening = true, isExecuting = true)
                NexusStatus.Error -> NexusUiState(error = "Ошибка работы сервиса")
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NexusUiState(isStopped = true)
        )

    fun startService(context: Context) {
        val intent = Intent(context, NexusListenService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopService(context: Context) {
        val intent = Intent(context, NexusListenService::class.java)
        context.stopService(intent)
    }
}