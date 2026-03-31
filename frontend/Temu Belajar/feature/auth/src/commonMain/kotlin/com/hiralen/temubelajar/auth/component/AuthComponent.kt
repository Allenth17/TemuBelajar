package com.hiralen.temubelajar.auth.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.DelicateDecomposeApi
import com.arkivanov.decompose.router.stack.*
import com.arkivanov.decompose.value.Value
import kotlinx.serialization.Serializable

/**
 * AuthComponent — Decompose component untuk auth flow.
 * Manage navigasi: Login ↔ Register → OTP → Home
 *
 * Navigation rules:
 *  - Login → Register: push (back goes to Login)
 *  - Register → OTP:   replaceCurrent (OTP replaces Register so back goes to Login, not Register)
 *  - OTP → Home:       onVerified() → replaceAll (handled by RootComponent)
 *  - OTP → Login:      replaceAll(Login) — user can restart registration if needed
 */
class AuthComponent(
    componentContext: ComponentContext,
    val onLoginSuccess: () -> Unit
) : ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()

    val stack: Value<ChildStack<Config, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Login,
        handleBackButton = true,
        childFactory = ::createChild
    )

    private fun createChild(config: Config, ctx: ComponentContext): Child {
        return when (config) {
            Config.Login -> Child.Login(LoginComponent(ctx, this))
            Config.Register -> Child.Register(RegisterComponent(ctx, this))
            is Config.OTP -> Child.OTP(OTPComponent(ctx, this, config.email))
        }
    }

    @OptIn(DelicateDecomposeApi::class)
    fun navigateToRegister() = navigation.push(Config.Register)

    /** Replace the entire back-stack with just Login — used after OTP back or logout. */
    fun navigateToLogin() = navigation.replaceAll(Config.Login)

    /**
     * Navigate to OTP screen, replacing Register in the back-stack.
     * Stack becomes: [Login, OTP] — pressing back goes to Login, not Register.
     */
    fun navigateToOTP(email: String) = navigation.replaceCurrent(Config.OTP(email))

    /** Called after successful OTP verification or direct login. */
    fun onVerified() = onLoginSuccess()

    // ─── Config ──────────────────────────────────────────────────────────────

    @Serializable
    sealed interface Config {
        @Serializable
        data object Login : Config
        @Serializable
        data object Register : Config
        @Serializable
        data class OTP(val email: String) : Config
    }

    // ─── Child ───────────────────────────────────────────────────────────────

    sealed interface Child {
        data class Login(val component: LoginComponent) : Child
        data class Register(val component: RegisterComponent) : Child
        data class OTP(val component: OTPComponent) : Child
    }
}
