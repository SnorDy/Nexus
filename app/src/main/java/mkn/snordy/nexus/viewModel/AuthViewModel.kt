package mkn.snordy.nexus.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

sealed interface AuthState {
    data object Idle : AuthState
    data object Loading : AuthState
    data class Success(val userName: String, val email: String?) : AuthState
    data class Error(val message: String) : AuthState
}

class AuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()
    private val client = OkHttpClient()

    fun handleYandexResult(token: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val (name, email) = fetchUserInfo(token)
                _authState.value = AuthState.Success(userName = name, email = email)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Ошибка получения данных")
            }
        }
    }

    private suspend fun fetchUserInfo(token: String): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://login.yandex.ru/info?format=json")
                .header("Authorization", "OAuth $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Ошибка сервера Яндекса: ${response.code}")

                val body = response.body?.string() ?: throw Exception("Пустой ответ")
                val json = JSONObject(body)

                val firstName = json.optString("login", "Пользователь")
                val email = json.optString("default_email").takeIf { it.isNotBlank() }

                firstName to email
            }
        }

    fun handleError(exception: Throwable) {
        _authState.value = AuthState.Error(exception.message ?: "Неизвестная ошибка")
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }
}