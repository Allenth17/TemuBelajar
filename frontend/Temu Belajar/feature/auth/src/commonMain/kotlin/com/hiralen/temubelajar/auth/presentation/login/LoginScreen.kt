package com.hiralen.temubelajar.auth.presentation.login

import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.auth.component.LoginComponent
import com.hiralen.temubelajar.core.presentation.isScrollableLayout
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun LoginScreen(component: LoginComponent) {
    val state by component.state.collectAsState()
    val isDark = isSystemInDarkTheme()
    var passwordVisible by remember { mutableStateOf(false) }

    val bgColor = if (isDark) TBColors.BackgroundDark else TBColors.Background
    val textPrimary = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary
    val textSecondary = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary
    val textMuted = if (isDark) TBColors.TextMutedDark else TBColors.TextMuted
    val accentColor = if (isDark) TBColors.PrimaryDark else TBColors.Primary

    // Conditional scroll: mobile scrolls, desktop/web does not
    val scrollModifier = if (isScrollableLayout) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Background decorative orbs (subtle, matches new palette)
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    Brush.radialGradient(
                        listOf(TBColors.Primary.copy(alpha = if (isDark) 0.10f else 0.06f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(50)
                )
        )
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 60.dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(TBColors.Secondary.copy(alpha = if (isDark) 0.08f else 0.05f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(50)
                )
        )

        // Center the card with max width for desktop/web
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .then(scrollModifier)
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(60.dp))

                TBLogoHeader(subtitle = "Temukan teman baru dari kampus berbeda\nReal-time video chat mahasiswa Indonesia")

                Spacer(Modifier.height(48.dp))

                TBCard {
                    // Email atau Username
                    TBTextField(
                        value = state.email,
                        onValueChange = component::onEmailChange,
                        label = "Email atau Username",
                        leadingIcon = TablerIcons.Mail,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    Text(
                        text = "Gunakan email kampus (uns, ui, ugm, itb, its)",
                        color = textMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )

                    Spacer(Modifier.height(14.dp))

                    // Password
                    TBTextField(
                        value = state.password,
                        onValueChange = component::onPasswordChange,
                        label = "Password",
                        leadingIcon = TablerIcons.Lock,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) TablerIcons.Eye else TablerIcons.EyeOff,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(Modifier.height(8.dp))

                    if (state.error != null) {
                        TBErrorBanner(state.error!!)
                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(8.dp))

                    TBGradientButton(
                        text = "Masuk",
                        onClick = component::login,
                        isLoading = state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Belum punya akun? ", color = textSecondary, fontSize = 14.sp)
                    Text(
                        text = "Daftar",
                        color = accentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = component::goToRegister)
                    )
                }

                Spacer(Modifier.height(60.dp))
            }
        }
    }
}
