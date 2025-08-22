package com.hiralen.temubelajar.auth.presentation.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiralen.temubelajar.auth.presentation.AuthViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import com.hiralen.temubelajar.auth.presentation.components.CustomAuthTextField
import com.hiralen.temubelajar.auth.presentation.components.HeaderText
import com.hiralen.temubelajar.core.presentation.defaultPadding
import com.hiralen.temubelajar.core.presentation.itemSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onRegisterSuccess: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val usernameState        = rememberSaveable { mutableStateOf("") }
    val passwordState        = rememberSaveable { mutableStateOf("") }
    val confirmPasswordState = rememberSaveable { mutableStateOf("") }

    val emailState      = rememberSaveable { mutableStateOf("") }
    val nameState       = rememberSaveable { mutableStateOf("") }
    val phoneState      = rememberSaveable { mutableStateOf("") }
    val universityState = rememberSaveable { mutableStateOf("") }

    val registerState by viewModel.registerState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(
        usernameState.value,
        passwordState.value,
        confirmPasswordState.value,

        emailState.value,
        nameState.value,
        phoneState.value,
        universityState.value
    ) {
        viewModel.updateUsername(usernameState.value)
        viewModel.updatePassword(passwordState.value)
        viewModel.updateConfirmPassword(confirmPasswordState.value)

        viewModel.updateEmail(emailState.value)
        viewModel.updateName(nameState.value)
        viewModel.updatePhone(phoneState.value)
        viewModel.updateUniversity(universityState.value)
    }

    LaunchedEffect(registerState) {
        when (val state = registerState) {
            is RegisterState.Success -> {
                onRegisterSuccess()
                usernameState.value = ""
                passwordState.value = ""
                confirmPasswordState.value = ""

                nameState.value = ""
                phoneState.value = ""
                universityState.value = ""
            }

            is RegisterState.Error -> {
                val error = state.message
                viewModel.resetRegisterState()
            }

            else -> {

            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(defaultPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderText(
            text = "Sign Up",
            modifier = modifier
                .padding(vertical = defaultPadding)
                .align(alignment = Alignment.CenterHorizontally)
        )
        CustomAuthTextField(
            value = emailState.value,
            onValueChange = { emailState.value = it },
            labelText = "Email",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = usernameState.value,
            onValueChange = { usernameState.value = it },
            labelText = "Username",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = nameState.value,
            onValueChange = { nameState.value = it },
            labelText = "Nama",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = universityState.value,
            onValueChange = { universityState.value = it },
            labelText = "Universitas",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = phoneState.value,
            onValueChange = { phoneState.value = it },
            labelText = "Nomor Telepon",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = passwordState.value,
            onValueChange = { passwordState.value = it },
            labelText = "Password",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Next,
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        CustomAuthTextField(
            value = confirmPasswordState.value,
            onValueChange = { confirmPasswordState.value = it },
            labelText = "Konfirmasi Password",
            modifier = modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done
        )
        Spacer(modifier = Modifier.height(itemSpacing))
        Button(
            onClick = {
                viewModel.register()
                focusManager.clearFocus()
            },
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors()
        ) {
            Text("Sign Up")
        }
        Spacer(modifier = Modifier.height(itemSpacing))
        val logString = "Login here"
        val loginString = buildAnnotatedString(
            builder = {
                withStyle(
                    style = SpanStyle()
                ) {
                    append(text = "Already have account?")
                }
                append(text = " ")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary
                    )
                ) {
                    pushStringAnnotation(
                        tag = logString,
                        annotation = logString
                    )
                    append(text = logString)
                }
            }
        )
        ClickableText(
            text = loginString
        ) { offset ->
            loginString.getStringAnnotations(
                offset, offset
            ).forEach {
                when(it.tag) {
                    logString -> {
                        onLoginClick()
                    }
                }
            }
        }
    }
}