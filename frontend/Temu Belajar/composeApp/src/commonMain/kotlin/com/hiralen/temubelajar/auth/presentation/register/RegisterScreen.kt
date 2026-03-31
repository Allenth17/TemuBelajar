package com.hiralen.temubelajar.auth.presentation.register

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
    val isDark = isSystemInDarkTheme()
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > 600.dp
        
        // Simplified check for success state
        val isSuccess = !state.isLoading && state.error == null && state.name.isNotEmpty()

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
                    .then(if (isWide) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(Modifier.height(40.dp))

            // Lottie Animation Header
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (isDark) TBColors.SurfaceDark else Color.White, RoundedCornerShape(32.dp))
                    .border(1.dp, if (isDark) TBColors.CardBorderDark else Color(0xFFF3F4F6), RoundedCornerShape(32.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                val animPath = if (state.isLoading) "files/robot_sync.json" 
                               else if (isSuccess) "files/checkmark.json" 
                               else "files/cat_peek.json" // Default
                
                TBLottie(
                    resPath = animPath,
                    modifier = Modifier.fillMaxSize(),
                    iterations = if (isSuccess) 1 else Int.MAX_VALUE
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = "Create an Account",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Sign up to connect with your peers\nthrough video calls.",
                fontSize = 15.sp,
                color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Adaptive Form Layout
            BoxWithConstraints(modifier = Modifier.widthIn(max = 1000.dp)) {
                val isWide = maxWidth > 600.dp
                
                if (isWide) {
                    // Desktop/Web Grid Layout
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        maxItemsInEachRow = 2
                    ) {
                        val itemModifier = Modifier.weight(1f).widthIn(min = 300.dp)
                        
                        TBTextField(state.name, component::onNameChange, "Full Name", itemModifier, "Enter your full name", TablerIcons.User)
                        TBTextField(state.username, component::onUsernameChange, "Username", itemModifier, "Choose a username", TablerIcons.At)
                        TBTextField(state.email, component::onEmailChange, "University Email", itemModifier, "you@univ.ac.id", TablerIcons.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        TBTextField(state.phone, component::onPhoneChange, "Phone Number", itemModifier, "e.g. 0812...", TablerIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        TBTextField(state.university, component::onUniversityChange, "University Name", itemModifier, "e.g. Universitas Indonesia", TablerIcons.BuildingArch)
                        
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
                            label = "Confirm Password",
                            placeholder = "Repeat your password",
                            modifier = itemModifier,
                            leadingIcon = TablerIcons.ShieldCheck,
                            trailingIcon = { PasswordToggle(confirmPasswordVisible) { confirmPasswordVisible = !confirmPasswordVisible } },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                } else {
                    // Mobile Column Layout
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TBTextField(state.name, component::onNameChange, "Full Name", placeholder = "Enter your full name", leadingIcon = TablerIcons.User)
                        TBTextField(state.username, component::onUsernameChange, "Username", placeholder = "Choose a username", leadingIcon = TablerIcons.At)
                        TBTextField(state.email, component::onEmailChange, "University Email", placeholder = "you@univ.ac.id", leadingIcon = TablerIcons.Mail, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                        TBTextField(state.phone, component::onPhoneChange, "Phone Number", placeholder = "e.g. 0812...", leadingIcon = TablerIcons.Phone, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                        TBTextField(state.university, component::onUniversityChange, "University Name", placeholder = "e.g. Universitas Indonesia", leadingIcon = TablerIcons.BuildingArch)
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
                            label = "Confirm Password",
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
                Spacer(Modifier.height(16.dp))
                TBErrorBanner(state.error!!, Modifier.widthIn(max = 600.dp))
            }

            Spacer(Modifier.height(40.dp))

            TBPrimaryButton(
                text = "Register",
                onClick = component::register,
                isLoading = state.isLoading,
                icon = TablerIcons.UserPlus,
                modifier = Modifier.widthIn(max = 400.dp)
            )

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Already have an account? ",
                    color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
                    fontSize = 14.sp
                )
                Text(
                    "Login",
                    color = TBColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = component::goToLogin)
                )
            }

            Spacer(Modifier.height(60.dp))
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
            tint = TBColors.Primary,
            modifier = Modifier.size(20.dp)
        )
    }
}