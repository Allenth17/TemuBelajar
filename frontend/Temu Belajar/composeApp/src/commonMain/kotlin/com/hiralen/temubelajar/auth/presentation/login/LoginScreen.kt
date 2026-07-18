package com.hiralen.temubelajar.auth.presentation.login

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.auth.component.LoginComponent
import com.hiralen.temubelajar.core.data.TokenStorage
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun LoginScreen(component: LoginComponent) {
    val state by component.state.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LinearColors.Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Phase 5.26 — IME inset so the on-screen keyboard never
                // covers the password field on small phones, and status bar
                // inset so the layout does not draw under the system status
                // bar under edge-to-edge (compose fills the whole window).
                .imePadding()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TBSpace.LG),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(TBSpace.XXL))

            val storage = remember { TokenStorage() }
            val hasLoggedIn = remember { storage.hasLoggedInBefore() }

            // Lottie peek card — surface-1 fill, hairline border, xl 16px corners.
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(TBShapes.XL)
                    .background(LinearColors.Surface1)
                    .border(1.dp, LinearColors.Hairline, TBShapes.XL)
                    .padding(TBSpace.MD),
                contentAlignment = Alignment.Center
            ) {
                TBLottie(
                    resPath = "files/cat_peek.json",
                    modifier = Modifier.fillMaxSize(),
                    iterations = Int.MAX_VALUE
                )
            }

            Spacer(Modifier.height(TBSpace.LG))

            Text(
                text = if (hasLoggedIn) "Welcome back" else "Find your study partner",
                style = TBTypography.Headline,
                color = LinearColors.Ink
            )

            Spacer(Modifier.height(TBSpace.SM))

            Text(
                text = if (hasLoggedIn)
                    "Pick up where you left off."
                else
                    "Sign in to start a video call with peers across campuses.",
                style = TBTypography.Body,
                color = LinearColors.InkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = TBSpace.MD)
            )

            Spacer(Modifier.height(TBSpace.XL))

            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(TBSpace.MD)
            ) {
                TBTextField(
                    value = state.email,
                    onValueChange = component::onEmailChange,
                    label = "Email",
                    placeholder = "you@campus.edu",
                    leadingIcon = TablerIcons.Mail,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                Column {
                    TBTextField(
                        value = state.password,
                        onValueChange = component::onPasswordChange,
                        label = "Password",
                        placeholder = "••••••••",
                        leadingIcon = TablerIcons.Lock,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) TablerIcons.Eye else TablerIcons.EyeOff,
                                    // Phase 5.29 — Talkback reads "Hide password" /
                                    // "Show password" instead of an unlabelled "Button".
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = LinearColors.InkSubtle,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                }

                if (state.error != null) {
                    TBErrorBanner(state.error!!)
                }

                Spacer(Modifier.height(TBSpace.SM))

                TBPrimaryButton(
                    // Phase 5.37 — mix of ID + EN labels in one auth flow is
                    // jarring; standardise on ID ("Masuk") to match the rest
                    // of the app ("Mulai mencari", "Menunggu video...", dst).
                    text = "Masuk",
                    onClick = component::login,
                    isLoading = state.isLoading,
                    icon = TablerIcons.ArrowRight
                )
                // Phase 5.11 — removed the "Continue with University ID"
                // placeholder and its accompanying "OR" eyebrow divider (the
                // divider only made sense as a separator before that SSO
                // button; without a second action it would be misleading
                // scaffolding pointing at a flow that does not exist).
            }

            Spacer(Modifier.height(TBSpace.XL))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // Phase 5.37 — match the rest of the IDs; EN fragment
                    // reads as a translation accident next to "Daftar".
                    "Belum punya akun? ",
                    color = LinearColors.InkMuted,
                    style = TBTypography.BodySM
                )
                Text(
                    "Daftar",
                    color = LinearColors.PrimaryHover,
                    style = TBTypography.BodySM.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.clickableRole(onClick = component::goToRegister)
                )
            }

            Spacer(Modifier.height(TBSpace.XXL))
        }
    }
}
