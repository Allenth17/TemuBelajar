package com.hiralen.temubelajar.auth.presentation.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiralen.temubelajar.auth.presentation.AuthViewModel
import com.hiralen.temubelajar.auth.presentation.components.CustomAuthTextField
import com.hiralen.temubelajar.auth.presentation.components.HeaderText
import com.hiralen.temubelajar.core.presentation.defaultPadding
import com.hiralen.temubelajar.core.presentation.itemSpacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import compose.icons.feathericons.Lock
import compose.icons.feathericons.User
import kotlin.plus

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emailState = rememberSaveable { mutableStateOf("") }
    val passwordState = rememberSaveable { mutableStateOf("") }
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(
        loginState
    ) {
        when(val state = loginState) {
            is LoginState.Success -> {
                onLoginSuccess()
                focusManager.clearFocus()
                viewModel.resetLoginState()
            }
            is LoginState.Error -> {
                viewModel.resetLoginState()
            }
            else ->  Unit
        }
    }
    LaunchedEffect(
        emailState.value,
        passwordState.value
    ) {
        viewModel.updateEmail(emailState.value)
        viewModel.updatePassword(passwordState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(defaultPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderText(
            text = "Login",
            modifier = Modifier
                .padding(vertical = defaultPadding)
                .align(alignment = Alignment.Start)
        )
        CustomAuthTextField(
            value = emailState.value,
            onValueChange = { emailState.value = it },
            labelText = "Username or Email",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            leadingIcon = {
                Icon(
                    imageVector = FeatherIcons.User,
                    contentDescription = null
                )
            }
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = passwordState.value,
            onValueChange = { passwordState.value = it },
            labelText = "Password",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            visualTransformation = if (passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
            leadingIcon = {
                Icon(
                    imageVector = FeatherIcons.Lock,
                    contentDescription = null
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = {
                        passwordVisible = !passwordVisible
                    }
                ) {
                    Icon(
                        imageVector = if (passwordVisible) FeatherIcons.EyeOff
                        else FeatherIcons.Eye,
                        contentDescription = null
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(defaultPadding + 8.dp))
        Button(
            onClick = {
                focusManager.clearFocus()
                viewModel.login()
            },
            modifier = modifier.fillMaxWidth(),
            enabled = loginState !is LoginState.Loading,
            colors = ButtonDefaults.buttonColors()
        ) {
            if (loginState is LoginState.Loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Sign In")
            }
        }
        Spacer(modifier = Modifier.height(itemSpacing))
        val regString = "Register here"
        val registerString = buildAnnotatedString(
            builder = {
                withStyle(
                    style = SpanStyle()
                ) {
                    append(text = "Don't have an account?")
                }
                append(text = " ")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary
                    )
                ) {
                    pushStringAnnotation(
                        tag = regString,
                        annotation = regString
                    )
                    append(text = regString)
                }
            }
        )
        ClickableText(
            text = registerString
        ) { offset ->
            registerString.getStringAnnotations(
                offset, offset
            ).forEach {
                when(it.tag) {
                    regString -> {
                        onRegisterClick()
                    }
                }
            }
        }
    }
}