package com.hiralen.temubelajar.auth.presentation

import androidx.compose.animation.*
import androidx.compose.runtime.*
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.*
import com.hiralen.temubelajar.auth.component.AuthComponent
import com.hiralen.temubelajar.auth.presentation.login.LoginScreen
import com.hiralen.temubelajar.auth.presentation.otp.OTPScreen
import com.hiralen.temubelajar.auth.presentation.register.RegisterScreen

@Composable
fun AuthContent(component: AuthComponent) {
    // Phase 5.39 — was: `slide()` for every auth transition. Lateral sliding
    //暗示 a back/forward hierarchy (like nested pages), but the auth flow is
    // a flat state machine: Login ↔ Register ↔ OTP. Sliding made the OTP
    // screen enter from the right when summoned after Login while it
    // logically replaces Login on the stack — felt jarring. A simple fade
    // better matches the modal/state-change semantics. Login→Register and
    // Register→OTP all read as gentle replacement anims; back navigation
    // also fades rather than slide-from-left, so the back gesture isn't
    // visually contradicted.
    Children(
        stack = component.stack,
        animation = stackAnimation(fade())
    ) { child ->
        when (val instance = child.instance) {
            is AuthComponent.Child.Login    -> LoginScreen(component = instance.component)
            is AuthComponent.Child.Register -> RegisterScreen(component = instance.component)
            is AuthComponent.Child.OTP      -> OTPScreen(component = instance.component)
        }
    }
}
