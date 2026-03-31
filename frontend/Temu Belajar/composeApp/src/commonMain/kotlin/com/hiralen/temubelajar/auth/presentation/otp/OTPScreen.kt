package com.hiralen.temubelajar.auth.presentation.otp

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
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

@Composable
fun OTPScreen(component: OTPComponent) {
    val state by component.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TBColors.Background)
    ) {
        // Orange Wave Background (consistent with Login/Register)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .clip(RoundedCornerShape(bottomStart = 80.dp, bottomEnd = 80.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(TBColors.Primary, Color(0xFFE35336).copy(alpha = 0.8f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TBCard(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .padding(vertical = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Area
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(TBColors.Primary.copy(alpha = 0.1f), RoundedCornerShape(30.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Using TBLottie for "robot_sync.json" or similar if available, 
                        // otherwise a premium Icon. For now, a styled icon.
                        Icon(
                            TablerIcons.ShieldCheck,
                            contentDescription = null,
                            tint = TBColors.Primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "Verify Email",
                        fontSize = 26.sp,
                        fontFamily = TBFonts.Outfit,
                        fontWeight = FontWeight.Bold,
                        color = TBColors.TextPrimary
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "We've sent a 6-digit code to\n${component.email}",
                        fontSize = 14.sp,
                        fontFamily = TBFonts.Outfit,
                        color = TBColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(32.dp))

                    // OTP Input Fields (Custom Large Styled)
                    OutlinedTextField(
                        value = state.otp,
                        onValueChange = { if (it.length <= 6) component.onOtpChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 16.sp,
                            color = TBColors.Primary,
                            fontFamily = TBFonts.Outfit
                        ),
                        placeholder = {
                            Text(
                                "000000",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                fontSize = 32.sp,
                                letterSpacing = 16.sp,
                                color = TBColors.TextHint,
                                fontWeight = FontWeight.Light,
                                fontFamily = TBFonts.Outfit
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TBColors.Primary,
                            unfocusedBorderColor = TBColors.CardBorder,
                            focusedContainerColor = TBColors.Background,
                            unfocusedContainerColor = TBColors.Background
                        ),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true
                    )

                    if (state.error != null) {
                        Spacer(Modifier.height(16.dp))
                        TBErrorBanner(state.error!!)
                    }

                    if (state.successMessage != null) {
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF4CAF50).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                state.successMessage!!, 
                                color = Color(0xFF2E7D32), 
                                fontSize = 13.sp, 
                                textAlign = TextAlign.Center, 
                                modifier = Modifier.fillMaxWidth(),
                                fontFamily = TBFonts.Outfit
                            )
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    TBPrimaryButton(
                        text = "Verify Code",
                        onClick = component::verify,
                        isLoading = state.isLoading,
                        enabled = state.otp.length == 6,
                        modifier = Modifier.fillMaxWidth(),
                        icon = TablerIcons.ArrowRight
                    )

                    Spacer(Modifier.height(24.dp))

                    // Resend Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Didn't receive code? ", 
                            color = TBColors.TextSecondary, 
                            fontSize = 14.sp,
                            fontFamily = TBFonts.Outfit
                        )
                        if (state.isResending) {
                            CircularProgressIndicator(
                                color = TBColors.Primary, 
                                modifier = Modifier.size(16.dp), 
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Resend",
                                color = TBColors.Primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TBFonts.Outfit,
                                modifier = Modifier.clickable(onClick = component::resend)
                            )
                        }
                    }
                }
            }
            
            // Helpful Tip
            Text(
                "Check your spam folder if you haven't received the email.",
                fontSize = 12.sp,
                color = TBColors.TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
                fontFamily = TBFonts.Outfit
            )
        }
    }
}