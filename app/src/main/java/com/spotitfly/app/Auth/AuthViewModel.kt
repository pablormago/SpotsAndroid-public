package com.spotitfly.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UsernameStatus { Idle, Checking, Free, Taken, Invalid }

data class LoginUi(
    val email: String = "",
    val pass: String = "",
    val loading: Boolean = false,
    val error: String? = null
)

data class RegisterUi(
    val username: String = "",
    val email: String = "",
    val pass: String = "",
    val accepted: Boolean = false,
    val usernameStatus: UsernameStatus = UsernameStatus.Idle,
    val loading: Boolean = false,
    val error: String? = null
)

class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _regPhotoUri = MutableStateFlow<android.net.Uri?>(null)
    val regPhotoUri = _regPhotoUri.asStateFlow()
    fun setRegPhoto(uri: android.net.Uri?) { _regPhotoUri.value = uri }


    private val _login = MutableStateFlow(LoginUi())
    val login = _login.asStateFlow()

    private val _register = MutableStateFlow(RegisterUi())
    val register = _register.asStateFlow()

    private var usernameJob: Job? = null

    fun setLoginEmail(v: String) { _login.value = _login.value.copy(email = v) }
    fun setLoginPass(v: String)  { _login.value = _login.value.copy(pass = v) }

    fun setRegUsername(v: String) {
        _register.value = _register.value.copy(username = v)
        debounceUsernameCheck(v)
    }
    fun setRegEmail(v: String)    { _register.value = _register.value.copy(email = v) }
    fun setRegPass(v: String)     { _register.value = _register.value.copy(pass = v) }
    fun setRegAccepted(v: Boolean){ _register.value = _register.value.copy(accepted = v) }

    private fun debounceUsernameCheck(raw: String) {
        usernameJob?.cancel()
        val candidate = raw.trim()
        if (candidate.isEmpty()) {
            _register.value = _register.value.copy(usernameStatus = UsernameStatus.Idle)
            return
        }
        // Filtro básico: alfanumérico + _ . y mínimo 3
        val valid = Regex("^[A-Za-z0-9_.]{3,}$").matches(candidate)
        if (!valid) {
            _register.value = _register.value.copy(usernameStatus = UsernameStatus.Invalid)
            return
        }
        _register.value = _register.value.copy(usernameStatus = UsernameStatus.Checking)
        usernameJob = viewModelScope.launch {
            delay(1000) // 1 segundo desde la última tecla
            val free = repo.isUsernameAvailable(candidate)
            _register.value = _register.value.copy(
                usernameStatus = if (free) UsernameStatus.Free else UsernameStatus.Taken
            )
        }
    }

    fun clearLoginError() { _login.value = _login.value.copy(error = null) }
    fun clearRegisterError() { _register.value = _register.value.copy(error = null) }

    fun signIn(onSuccess: () -> Unit) = viewModelScope.launch {
        try {
            _login.value = _login.value.copy(loading = true, error = null)
            repo.signIn(_login.value.email, _login.value.pass)
            // Bloqueamos mapa si no está verificado (el AppRoot decide)
            onSuccess()
        } catch (e: Exception) {
            _login.value = _login.value.copy(error = e.localizedMessage ?: "Error al iniciar sesión")
        } finally {
            _login.value = _login.value.copy(loading = false)
        }
    }

    fun register(onSuccessNeedsEmailVerify: () -> Unit) = viewModelScope.launch {
        try {
            _register.value = _register.value.copy(loading = true, error = null)
            val r = _register.value
            // Requiere username Free y aceptar normas
            if (r.usernameStatus != UsernameStatus.Free) {
                _register.value = _register.value.copy(error = "El nombre de usuario no está disponible")
                return@launch
            }
            if (!r.accepted) {
                _register.value = _register.value.copy(error = "Debes aceptar las normas")
                return@launch
            }
            repo.register(r.username, r.email, r.pass, r.accepted)
            // Upload profile photo if present
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val localUri = _regPhotoUri.value
            if (uid != null && localUri != null) {
                val url = repo.uploadProfilePhoto(uid, localUri)
                repo.updateUserPhotoUrl(uid, url)
            }
            onSuccessNeedsEmailVerify()
        } catch (e: Exception) {
            _register.value = _register.value.copy(error = e.localizedMessage ?: "Error al registrarse")
        } finally {
            _register.value = _register.value.copy(loading = false)
        }
    }

    fun sendReset(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        val current = login.value.email
        if (current.isNullOrBlank()) {
            onResult(false, "Introduce un email válido")
            return
        }
        sendPasswordReset(current, onResult)
    }

    suspend fun isEmailVerified(): Boolean = repo.isEmailVerified()
    fun signOut() = repo.signOut()

    fun sendPasswordReset(
        email: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            try {
                repo.sendPasswordResetEmail(email)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message)
            }
        }
    }
    /**
     * Lógica de notificaciones ligada al login.
     * Llamamos desde la capa de UI tras pedir el permiso de sistema.
     */
    fun handleNotificationsAfterLogin(granted: Boolean) {
        viewModelScope.launch {
            try {
                repo.initNotificationsAfterLogin(granted)
            } catch (_: Exception) {
                // Ignoramos errores de registro de notificaciones en el flujo de login
            }
        }
    }
}



