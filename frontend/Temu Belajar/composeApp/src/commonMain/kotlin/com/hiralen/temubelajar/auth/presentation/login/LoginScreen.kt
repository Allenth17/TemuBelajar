package com.hiralen.temubelajar.auth.presentation.login

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) TBColors.BackgroundDark else Color(0xFFFCF9F7))
    ) {
        // Orange Wave Background (Bottom)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(180.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            TBColors.Primary.copy(alpha = 0.1f),
                            TBColors.Primary.copy(alpha = 0.4f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(40.dp))

            val storage = remember { TokenStorage() }
            val hasLoggedIn = remember { storage.hasLoggedInBefore() }

            // Lottie Animation: cat_peek.json as Header Icon
            TBLottie(
                resPath = "files/cat_peek.json",
                modifier = Modifier
                    .size(200.dp) // Enlarged as requested
                    .clip(RoundedCornerShape(40.dp))
                    .background(if (isDark) TBColors.SurfaceDark else Color.White, RoundedCornerShape(40.dp))
                    .border(1.dp, if (isDark) TBColors.CardBorderDark else Color(0xFFF3F4F6), RoundedCornerShape(40.dp))
                    .padding(24.dp),
                iterations = Int.MAX_VALUE
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = if (hasLoggedIn) "Welcome Back!" else "Welcome to TemuBelajar",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Sign in to start a video call with your friends,\nfamily, and peers.",
                fontSize = 15.sp,
                color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(48.dp))

            Column(
                modifier = Modifier.widthIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TBTextField(
                    value = state.email,
                    onValueChange = component::onEmailChange,
                    label = "Email",
                    placeholder = "you@email.com",
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
                                    contentDescription = null,
                                    tint = TBColors.Primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    
                    Text(
                        text = "Forgot Password?",
                        color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 8.dp)
                            .clickable { /* TODO */ }
                    )
                }

                if (state.error != null) {
                    TBErrorBanner(state.error!!)
                }

                Spacer(Modifier.height(12.dp))

                TBPrimaryButton(
                    text = "Login",
                    onClick = component::login,
                    isLoading = state.isLoading,
                    icon = TablerIcons.Video
                )

                // Divider "or"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                    Text(" or ", color = TBColors.TextMuted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f))
                }

                // University Login Hint (Replacement for Google)
                OutlinedButton(
                    onClick = { /* University SSO if any */ },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = TBShapes.Button,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TBColors.TextPrimary),
                    border = BorderStroke(1.dp, if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.1f))
                ) {
                    Text("Login with University ID", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Don't have an account? ",
                    color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "Create Account",
                    color = TBColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = component::goToRegister)
                )
            }
            
            Spacer(Modifier.height(60.dp))
        }
    }
}