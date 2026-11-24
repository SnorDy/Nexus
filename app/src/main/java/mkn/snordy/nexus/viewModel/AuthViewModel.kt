package mkn.snordy.nexus.viewModel

import android.content.Context

import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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


    fun signInWithGoogle(context: Context, webClientId: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = performGoogleSignIn(context, webClientId)
            result.onSuccess { userName ->
                // Получаем email из Firebase
                val email = FirebaseAuth.getInstance().currentUser?.email
                _authState.value = AuthState.Success(userName = userName, email = email)
            }.onFailure { error ->
                _authState.value = AuthState.Error(error.message ?: "Ошибка входа через Google")
            }
        }
    }

    private suspend fun performGoogleSignIn(context: Context, webClientId: String): Result<String> {
        val credentialManager = CredentialManager.create(context)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(webClientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = context,
                request = request,
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Передаем токен в Firebase
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()

                Result.success(authResult.user?.displayName ?: "Пользователь Google")
            } else {
                Result.failure(Exception("Неизвестный тип учетных данных"))
            }
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
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