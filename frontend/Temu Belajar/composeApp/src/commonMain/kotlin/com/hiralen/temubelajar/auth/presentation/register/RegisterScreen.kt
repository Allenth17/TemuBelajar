package com.hiralen.temubelajar.auth.presentation.register

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.auth.component.RegisterComponent
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(component: RegisterComponent) {
    val state by component.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > 600.dp
        // Phase 5.14 — `isSuccess` is now explicit component state, set only
        // after `repository.register()` succeeds. Was previously a derived
        // value that turned true as soon as the user typed a name. Declaring
        // it locally keeps the lambda-cleanups below readable.
        val isSuccess = state.isSuccess

        Box(modifier = Modifier.fillMaxSize().background(LinearColors.Canvas)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Phase 5.26 — IME / status-bar insets so the on-screen
                    // keyboard never covers the submit/continue button and the
                    // form does not draw under the system status bar under
                    // edge-to-edge.
                    .imePadding()
                    .statusBarsPadding()
                    .then(if (isWide) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(horizontal = TBSpace.LG),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(TBSpace.XXL))

                // Hero card — surface-1 panel, hairline, xl 16px corners.
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(TBShapes.XL)
                        .background(LinearColors.Surface1)
                        .border(1.dp, LinearColors.Hairline, TBShapes.XL)
                        .padding(TBSpace.SM),
                    contentAlignment = Alignment.Center
                ) {
                    val animPath = when {
                        state.isLoading -> "files/robot_sync.json"
                        isSuccess -> "files/checkmark.json"
                        else -> "files/cat_peek.json"
                    }
                    TBLottie(
                        resPath = animPath,
                        modifier = Modifier.fillMaxSize(),
                        iterations = if (isSuccess) 1 else Int.MAX_VALUE
                    )
                }

                Spacer(Modifier.height(TBSpace.LG))

                Text(
                    text = "Create your account",
                    style = TBTypography.Headline,
                    color = LinearColors.Ink
                )
                Spacer(Modifier.height(TBSpace.SM))
                Text(
                    text = "Sign up to connect with peers through video calls.",
                    style = TBTypography.Body,
                    color = LinearColors.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = TBSpace.MD)
                )

                Spacer(Modifier.height(TBSpace.XL))

                BoxWithConstraints(modifier = Modifier.widthIn(max = 1000.dp)) {
                    val isWideInner = maxWidth > 600.dp
                    if (isWideInner) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(TBSpace.LG),
                            verticalArrangement = Arrangement.spacedBy(TBSpace.MD),
                            maxItemsInEachRow = 2
                        ) {
                            val itemModifier = Modifier.weight(1f).widthIn(min = 280.dp)
                            TBTextField(state.name, component::onNameChange, "Full name", itemModifier, "Enter your full name", TablerIcons.User)
                            TBTextField(state.username, component::onUsernameChange, "Username", itemModifier, "Choose a username", TablerIcons.At)
                            TBTextField(state.email, component::onEmailChange, "University email", itemModifier, "you@univ.ac.id", TablerIcons.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            TBTextField(state.phone, component::onPhoneChange, "Phone number", itemModifier, "e.g. 0812…", TablerIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            TBTextField(state.university, component::onUniversityChange, "University name", itemModifier, "e.g. Universitas Indonesia", TablerIcons.BuildingArch)
                            TBTextField(
                                value = state.password,
                                onValueChange = component::onPasswordChange,
                                label = "Password",
                                placeholder = "Create a password",
                                modifier = itemModifier,
                                leadingIcon = TablerIcons.Lock,
                                trailingIcon = { PasswordToggle(passwordVisible) { passwordVisible = !passwordVisible } },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            TBTextField(
                                value = state.confirmPassword,
                                onValueChange = component::onConfirmPasswordChange,
                                label = "Confirm password",
                                placeholder = "Repeat your password",
                                modifier = itemModifier,
                                leadingIcon = TablerIcons.ShieldCheck,
                                trailingIcon = { PasswordToggle(confirmPasswordVisible) { confirmPasswordVisible = !confirmPasswordVisible } },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(TBSpace.MD)
                        ) {
                            TBTextField(state.name, component::onNameChange, "Full name", placeholder = "Enter your full name", leadingIcon = TablerIcons.User)
                            TBTextField(state.username, component::onUsernameChange, "Username", placeholder = "Choose a username", leadingIcon = TablerIcons.At)
                            TBTextField(state.email, component::onEmailChange, "University email", placeholder = "you@univ.ac.id", leadingIcon = TablerIcons.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            TBTextField(state.phone, component::onPhoneChange, "Phone number", placeholder = "e.g. 0812…", leadingIcon = TablerIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            TBTextField(state.university, component::onUniversityChange, "University name", placeholder = "e.g. Universitas Indonesia", leadingIcon = TablerIcons.BuildingArch)
                            TBTextField(
                                value = state.password,
                                onValueChange = component::onPasswordChange,
                                label = "Password",
                                placeholder = "Create a password",
                                leadingIcon = TablerIcons.Lock,
                                trailingIcon = { PasswordToggle(passwordVisible) { passwordVisible = !passwordVisible } },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                            TBTextField(
                                value = state.confirmPassword,
                                onValueChange = component::onConfirmPasswordChange,
                                label = "Confirm password",
                                placeholder = "Repeat your password",
                                leadingIcon = TablerIcons.ShieldCheck,
                                trailingIcon = { PasswordToggle(confirmPasswordVisible) { confirmPasswordVisible = !confirmPasswordVisible } },
                                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )
                        }
                    }
                }

                if (state.error != null) {
                    Spacer(Modifier.height(TBSpace.MD))
                    TBErrorBanner(state.error!!, Modifier.widthIn(max = 600.dp))
                }

                Spacer(Modifier.height(TBSpace.XL))

                // Phase 5.14 — after the server accepts registration, swap the
                // submit button for a "continue to OTP" action so the user can
                // dismiss the success Lottie and move on. The component's
                // `proceedToOtp()` clears the success flag and navigates.
                if (isSuccess) {
                    TBPrimaryButton(
                        text = "Lanjut ke verifikasi",
                        onClick = component::proceedToOtp,
                        icon = TablerIcons.ArrowRight,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                } else {
                    TBPrimaryButton(
                        // Phase 5.37 — match ID elsewhere in the auth flow.
                        text = "Daftar",
                        onClick = component::register,
                        isLoading = state.isLoading,
                        icon = TablerIcons.UserPlus,
                        modifier = Modifier.widthIn(max = 400.dp)
                    )
                }

                Spacer(Modifier.height(TBSpace.LG))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        // Phase 5.37 — match IDs elsewhere in auth flow.
                        "Sudah punya akun? ",
                        color = LinearColors.InkMuted,
                        style = TBTypography.BodySM
                    )
                    Text(
                        "Masuk",
                        color = LinearColors.PrimaryHover,
                        style = TBTypography.BodySM.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.clickableRole(onClick = component::goToLogin)
                    )
                }

                Spacer(Modifier.height(TBSpace.XXL))
            }
        }
    }
}

@Composable
private fun PasswordToggle(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (visible) TablerIcons.Eye else TablerIcons.EyeOff,
            contentDescription = null,
            tint = LinearColors.InkSubtle,
            modifier = Modifier.size(18.dp)
        )
    }
}
