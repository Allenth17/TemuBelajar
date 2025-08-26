package com.hiralen.temubelajar.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiralen.temubelajar.app.Destination
import com.hiralen.temubelajar.app.Navigator
import com.hiralen.temubelajar.auth.domain.Account
import com.hiralen.temubelajar.auth.domain.AccountLogin
import com.hiralen.temubelajar.auth.domain.AccountRegister
import com.hiralen.temubelajar.auth.domain.AccountRepository
import com.hiralen.temubelajar.auth.domain.LoginResponse
import com.hiralen.temubelajar.auth.domain.Message
import com.hiralen.temubelajar.auth.presentation.login.LoginState
import com.hiralen.temubelajar.auth.presentation.me.MeState
import com.hiralen.temubelajar.auth.presentation.otp.OTPState
import com.hiralen.temubelajar.auth.presentation.otp.ROTPState
import com.hiralen.temubelajar.auth.presentation.register.RegisterState
import com.hiralen.temubelajar.core.domain.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val accountRepository: AccountRepository,
    private val navigator: Navigator,
) : ViewModel() {

    init {
        checkSession()
    }

    private var username        : String = ""
    private var password        : String = ""
    private var confirmPassword : String = ""
    private var email           : String = ""
    private var name            : String = ""
    private var phone           : String = ""
    private var university      : String = ""
    private var otp             : String = ""

    private val _emailState = MutableStateFlow("")
    val emailState: StateFlow<String> = _emailState

    private val _registerState = MutableStateFlow<RegisterState<Message>>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState<Message>> = _registerState.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState<LoginResponse>>(LoginState.Idle)
    val loginState: StateFlow<LoginState<LoginResponse>> = _loginState.asStateFlow()

    private val _otpState = MutableStateFlow<OTPState<Message>>(OTPState.Idle)
    val otpState: StateFlow<OTPState<Message>> = _otpState.asStateFlow()

    private val _rOtpState = MutableStateFlow<ROTPState<Message>>(ROTPState.Idle)
    val rOtpState: StateFlow<ROTPState<Message>> = _rOtpState.asStateFlow()

    private val _meState = MutableStateFlow<MeState<Account>>(MeState.Idle)
    val meState: StateFlow<MeState<Account>> = _meState.asStateFlow()

    fun updateUsername        (username        : String) { this.username        = username.trim()        }
    fun updatePassword        (password        : String) { this.password        = password.trim()        }
    fun updateConfirmPassword (confirmPassword : String) { this.confirmPassword = confirmPassword.trim() }
    fun updateEmail           (email           : String) { this.email           = email.trim();
                                                           _emailState.value    = email.trim()           }

    fun updateName            (name            : String) { this.name            = name.trim()            }
    fun updatePhone           (phone           : String) { this.phone           = phone.trim()           }
    fun updateUniversity      (university      : String) { this.university      = university.trim()      }
    fun updateOtp             (otp             : String) { this.otp             = otp.trim()             }

    fun resetRegisterState() { _registerState.value = RegisterState.Idle }
    fun resetOtpState() { _otpState.value = OTPState.Idle }
    fun resetLoginState() { _loginState.value = LoginState.Idle }

    fun register() {
        if (!validateForm()) return
        viewModelScope.launch {
            try {
                _registerState.value = RegisterState.Loading
                val result = accountRepository.register(
                    accountRegister = AccountRegister(
                        email = email,
                        password = password,
                        username = username,
                        name = name,
                        phone = phone,
                        university = university
                    )
                )

                when (result) {
                    is Result.Success -> {
                        _registerState.value = RegisterState.Success(result.data)
                    }

                    is Result.Error -> {
                        _registerState.value = RegisterState.Error(result.error.toString())
                    }
                }
            } catch (e: Exception) {
                _registerState.value = RegisterState.Error(e.message ?: "An unexpected error occurred")
            }
        }
    }

    fun verifyOtp() {
        viewModelScope.launch {
            _otpState.value = OTPState.Loading
            val result = accountRepository.verifyOtp(
                email = email,
                otp = otp
            )
            when (result) {
                is Result.Success -> {
                    _otpState.value = OTPState.Success(result.data)
                }
                is Result.Error -> {
                    _otpState.value = OTPState.Error(result.error.toString())
                }
            }
        }
    }

    fun resendOtp() {
        viewModelScope.launch {
            _rOtpState.value = ROTPState.Loading
            val result = accountRepository.resendOtp(
                email = email
            )
            when (result) {
                is Result.Success -> {
                    _rOtpState.value = ROTPState.Success(result.data)
                }
                is Result.Error -> {
                    _rOtpState.value = ROTPState.Error(result.error.toString())
                }
            }
        }
    }
    private fun validateForm() : Boolean {
        return when {
            username.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("Username cannot be empty")
                false
            }
            password.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("Password cannot be empty")
                false
            }
            password != confirmPassword -> {
                _registerState.value = RegisterState
                    .Error("Passwords do not match")
                false
            }
            name.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("Name cannot be empty")
                false
            }
            phone.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("Phone number cannot be empty")
                false
            }
            email.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("Email cannot be empty")
                false
            }
            university.isEmpty() -> {
                _registerState.value = RegisterState
                    .Error("University cannot be empty")
                false
            }
            else -> true
        }
    }

    fun login() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = accountRepository.login(
                accountLogin = AccountLogin(
                    emailOrUsername = email,
                    password = password
                )
            )
            when (result) {
                is Result.Success -> {
                    _loginState.value = LoginState.Success(result.data)
                    accountRepository.saveToken(result.data.token)
                }
                is Result.Error -> {
                    _loginState.value = LoginState.Error(result.error.toString())
                }
            }
        }
    }

    fun me() {
        viewModelScope.launch {
            val token = getStoredToken()
            when {
                token != null -> {
                    accountRepository.me(token)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val token = getStoredToken()
            when {
                token != null -> {
                    accountRepository.logout(token)
                    accountRepository.clearToken(token)
                }
            }
        }
    }

    fun navigateToLogin() {
        viewModelScope.launch {
            navigator.navigate(
                destination = Destination.LoginPage,
                navOptions = {
                    popUpTo(Destination.LoginPage) {
                        inclusive = true
                    }
                }
            )
        }
    }

    fun navigateToRegister() {
        viewModelScope.launch {
            navigator.navigate(
                destination = Destination.RegisterPage,
                navOptions = {
                    popUpTo(Destination.LoginPage) {
                        inclusive = true
                    }
                }
            )
        }
    }
    fun navigateToHome() {
        viewModelScope.launch {
            navigator.navigate(
                destination = Destination.MainGraph,
                navOptions = {
                    popUpTo(Destination.AuthGraph) {
                        inclusive = true
                    }
                }
            )
        }
    }
    fun navigateToOTP() {
        viewModelScope.launch {
            navigator.navigate(
                destination = Destination.OTPPage,
                navOptions = {
                    popUpTo(Destination.RegisterPage) {
                        inclusive = true
                    }
                }
            )
        }
    }
    suspend fun getStoredToken(): String? {
        return accountRepository.getToken()?.token
    }
    fun checkSession() {
        viewModelScope.launch {
            try {
                val token = getStoredToken()
                when {
                    token != null -> {
                        val result = accountRepository.me(token)
                        when (result) {
                            is Result.Success -> {
                                navigateToHome()
                            }
                            is Result.Error -> {
                                accountRepository.clearToken(token)
                                navigateToLogin()
                            }
                        }
                    }
                    else -> {
                        navigateToLogin()
                    }
                }
            } catch(e: Exception) {
                navigateToLogin()
            }
        }
    }
}