package com.hiralen.temubelajar.auth.presentation.register

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
import com.hiralen.temubelajar.auth.component.RegisterComponent
import com.hiralen.temubelajar.core.presentation.isScrollableLayout
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

@Composable
fun RegisterScreen(component: RegisterComponent) {
    val state by component.state.collectAsState()
    val isDark = isSystemInDarkTheme()
    var passwordVisible by remember { mutableStateOf(false) }

    val bgColor = if (isDark) TBColors.BackgroundDark else TBColors.Background
    val textPrimary = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary
    val textSecondary = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary
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
        // Decorative gradient orb — subtle teal
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 80.dp, y = (-60).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            TBColors.Secondary.copy(alpha = if (isDark) 0.10f else 0.06f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
        )
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            TBColors.Primary.copy(alpha = if (isDark) 0.08f else 0.05f),
                            Color.Transparent
                        )
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(52.dp))

                // Back button row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = component::goToLogin) {
                        Icon(
                            TablerIcons.ArrowLeft,
                            contentDescription = "Kembali",
                            tint = textSecondary
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Buat Akun",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary
                )
                Text(
                    "Bergabung dengan ribuan mahasiswa",
                    fontSize = 14.sp,
                    color = textSecondary
                )

                Spacer(Modifier.height(28.dp))

                TBCard {
                    TBTextField(
                        value = state.name,
                        onValueChange = component::onNameChange,
                        label = "Nama Lengkap",
                        leadingIcon = TablerIcons.User
                    )
                    Spacer(Modifier.height(12.dp))

                    TBTextField(
                        value = state.username,
                        onValueChange = component::onUsernameChange,
                        label = "Username",
                        leadingIcon = TablerIcons.At
                    )
                    Spacer(Modifier.height(12.dp))

                    TBTextField(
                        value = state.email,
                        onValueChange = component::onEmailChange,
                        label = "Email Kampus",
                        leadingIcon = TablerIcons.Mail,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(Modifier.height(12.dp))

                    TBTextField(
                        value = state.phone,
                        onValueChange = component::onPhoneChange,
                        label = "Nomor HP",
                        leadingIcon = TablerIcons.Phone,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(Modifier.height(12.dp))

                    TBTextField(
                        value = state.university,
                        onValueChange = component::onUniversityChange,
                        label = "Nama Universitas",
                        leadingIcon = TablerIcons.BuildingArch
                    )
                    Spacer(Modifier.height(12.dp))

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
                    Spacer(Modifier.height(12.dp))

                    TBTextField(
                        value = state.confirmPassword,
                        onValueChange = component::onConfirmPasswordChange,
                        label = "Konfirmasi Password",
                        leadingIcon = TablerIcons.Lock,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        TBErrorBanner(state.error!!)
                    }

                    Spacer(Modifier.height(20.dp))

                    TBGradientButton(
                        text = "Daftar Sekarang",
                        onClick = component::register,
                        isLoading = state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sudah punya akun? ", color = textSecondary, fontSize = 14.sp)
                    Text(
                        text = "Masuk",
                        color = accentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = component::goToLogin)
                    )
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
