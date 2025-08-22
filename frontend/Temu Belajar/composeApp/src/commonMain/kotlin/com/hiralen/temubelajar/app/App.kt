package com.hiralen.temubelajar.app

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.navigation
import com.hiralen.temubelajar.CameraView
import com.hiralen.temubelajar.auth.presentation.AuthViewModel
import com.hiralen.temubelajar.auth.presentation.login.LoginScreen
import com.hiralen.temubelajar.auth.presentation.otp.OTPScreen
import com.hiralen.temubelajar.auth.presentation.register.RegisterScreen
import com.hiralen.temubelajar.core.presentation.ObserveAsEvents
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
//    CameraView()
    val navController = rememberNavController()
    val navigator = koinInject<Navigator>()
    val authViewModel = koinViewModel<AuthViewModel>()
    ObserveAsEvents(
        events = navigator.navigationActions
    ) { action ->
        when (action) {
            is NavigationAction.Navigate -> {
                navController.navigate(
                    action.destination
                ) {
                    action.navOptions(this)
                }
            }
            NavigationAction.NavigateUp -> {
                navController.navigateUp()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.AuthGraph
    ) {
        navigation<Destination.AuthGraph>(
            startDestination = Destination.LoginPage
        ) {
            composable<Destination.LoginPage> {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = {
                        authViewModel.navigateToHome()
                    },
                    onRegisterClick = {
                        authViewModel.navigateToRegister()
                    }
                )
            }
            composable<Destination.RegisterPage> {
                RegisterScreen(
                    viewModel = authViewModel,
                    onRegisterSuccess = {
                        authViewModel.navigateToOTP()
                    },
                    onLoginClick = {
                        authViewModel.navigateToLogin()
                    }
                )
            }
            composable<Destination.OTPPage> {
                OTPScreen(
                    viewModel = authViewModel,
                    onVerificationSuccess = {
                        authViewModel.navigateToLogin()
                    }
                )
            }
            composable<Destination.ForgotPasswordPage> {

            }
        }
        navigation<Destination.MainGraph>(
            startDestination = Destination.HomePage
        ) {
            composable<Destination.HomePage> {
                CameraView()
            }
            composable<Destination.ProfilePage> {

            }
        }
    }
}


@Composable
private inline fun <reified T: ViewModel> NavBackStackEntry.sharedKoinViewModel(
    navController: NavController
): T {
    val navGraphRoute = destination.parent?.route ?: return koinViewModel<T>()
    val parentEntry = remember(this) {
        navController.getBackStackEntry(navGraphRoute)
    }
    return koinViewModel(
        viewModelStoreOwner = parentEntry
    )
}