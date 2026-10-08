package mkn.snordy.nexus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NexusStatus { Stopped, Loading, Listening, WakeDetected, Error }

object NexusState {
    private val _status = MutableStateFlow(NexusStatus.Stopped)
    val status: StateFlow<NexusStatus> = _status.asStateFlow()

    private val _lastCommand = MutableStateFlow("")
    val lastCommand: StateFlow<String> = _lastCommand.asStateFlow()

    fun set(s: NexusStatus) { _status.value = s }
    fun setCommand(text: String) { _lastCommand.value = text }
}