package com.hiralen.temubelajar.auth.presentation.otp

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hiralen.temubelajar.auth.component.OTPComponent
import com.hiralen.temubelajar.core.ui.*
import compose.icons.TablerIcons
import compose.icons.tablericons.*

/**
 * OTPScreen — email verification screen shown after registration.
 *
 * Back navigation is intentionally routed to Login (not Register) via
 * [OTPComponent.navigateBackToLogin]. This prevents users from going back
 * to a completed registration form and submitting it again.
 *
 * The system back button also goes to Login because AuthComponent.navigateToOTP
 * uses replaceCurrent — so the back-stack is [Login, OTP], not [Login, Register, OTP].
 */
@Composable
fun OTPScreen(component: OTPComponent) {
    val state by component.state.collectAsState()
    val isDark = isSystemInDarkTheme()

    val bgColor = if (isDark) TBColors.BackgroundDark else TBColors.Background
    val textPrimary = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary
    val textSecondary = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary
    val accentColor = if (isDark) TBColors.PrimaryDark else TBColors.Primary
    val iconBgColor = if (isDark) TBColors.PrimaryContainerDark else TBColors.PrimaryContainer
    val iconBorderColor = accentColor.copy(alpha = 0.4f)

    // Gentle pulsing animation for the email icon
    val infiniteTransition = rememberInfiniteTransition(label = "otp_pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Decorative gradient wash at the top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isDark) 0.08f else 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(52.dp))

            // ── "Kembali ke Login" row at the top ────────────────────────────
            // OTP back navigation goes to Login, NOT Register.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = component::navigateBackToLogin) {
                    Icon(
                        TablerIcons.ArrowLeft,
                        contentDescription = "Kembali ke Login",
                        tint = textSecondary
                    )
                }
                Text(
                    text = "Kembali ke Login",
                    color = textSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            // ── Pulsing mail icon ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .scale(pulse)
                    .size(88.dp)
                    .background(iconBgColor, RoundedCornerShape(24.dp))
                    .border(1.dp, iconBorderColor, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    TablerIcons.Mail,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Verifikasi Email",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Masukkan kode OTP yang dikirim ke\n${component.email}",
                fontSize = 14.sp,
                color = textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))

            // ── Card ──────────────────────────────────────────────────────────
            TBCard {
                // Large centred OTP input
                OutlinedTextField(
                    value = state.otp,
                    onValueChange = component::onOtpChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        letterSpacing = 12.sp,
                        color = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary
                    ),
                    placeholder = {
                        Text(
                            "000000",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            fontSize = 32.sp,
                            letterSpacing = 12.sp,
                            color = if (isDark) TBColors.TextHintDark else TBColors.TextHint
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = if (isDark) TBColors.CardBorderDark else TBColors.CardBorder,
                        focusedContainerColor = if (isDark) TBColors.GlassLight else Color(0xFFF9FAFB),
                        unfocusedContainerColor = if (isDark) TBColors.GlassLight else Color(0xFFF9FAFB),
                        cursorColor = accentColor
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    isError = state.error != null
                )

                // Error banner
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    TBErrorBanner(state.error!!)
                }

                // Success banner
                if (state.successMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    TBSuccessBanner(state.successMessage!!)
                }

                Spacer(Modifier.height(20.dp))

                TBGradientButton(
                    text = "Verifikasi",
                    onClick = component::verify,
                    isLoading = state.isLoading,
                    enabled = state.otp.length == 6,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Resend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Belum dapat kode? ",
                        color = textSecondary,
                        fontSize = 13.sp
                    )
                    if (state.isResending) {
                        CircularProgressIndicator(
                            color = accentColor,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Kirim ulang",
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = component::resend)
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
