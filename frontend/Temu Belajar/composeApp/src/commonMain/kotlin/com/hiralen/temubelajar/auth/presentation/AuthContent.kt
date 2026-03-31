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
    Children(
        stack = component.stack,
        animation = stackAnimation(slide())
    ) { child ->
        when (val instance = child.instance) {
            is AuthComponent.Child.Login    -> LoginScreen(component = instance.component)
            is AuthComponent.Child.Register -> RegisterScreen(component = instance.component)
            is AuthComponent.Child.OTP      -> OTPScreen(component = instance.component)
        }
    }
}
