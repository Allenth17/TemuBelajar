package com.hiralen.temubelajar.core.ui

import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.Compottie
import org.jetbrains.compose.resources.*
import temubelajar.core.generated.resources.Res
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Primary Button ───────────────────────────────────────────────────────────

@Composable
fun TBPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = TBColors.Primary,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = TBShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = containerColor.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 4.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

// ─── Premium Text Field ───────────────────────────────────────────────────────

@Composable
fun TBTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    singleLine: Boolean = true
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) TBColors.TextPrimaryDark else TBColors.TextPrimary
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) TBColors.TextSecondaryDark else TBColors.TextSecondary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TBColors.TextHint, fontSize = 15.sp) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(icon, null, tint = TBColors.Primary, modifier = Modifier.size(20.dp)) }
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            isError = isError,
            singleLine = singleLine,
            shape = TBShapes.Input,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TBColors.Primary,
                unfocusedBorderColor = if (isDark) TBColors.CardBorderDark else Color(0xFFE5E7EB),
                errorBorderColor = TBColors.Error,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = if (isDark) Color.Black.copy(0.2f) else Color(0xFFF9FAFB),
                unfocusedContainerColor = if (isDark) Color.Black.copy(0.2f) else Color(0xFFF9FAFB)
            )
        )
    }
}

// ─── Card ─────────────────────────────────────────────────────────────────────

@Composable
fun TBCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) TBColors.CardBgDark else TBColors.CardBg
    val border = if (isDark) TBColors.CardBorderDark else TBColors.CardBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .padding(20.dp),
        content = content
    )
}

// ─── Error Banner ─────────────────────────────────────────────────────────────

@Composable
fun TBErrorBanner(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TBColors.Error.copy(alpha = 0.12f))
                .border(1.dp, TBColors.Error.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = TBColors.Error,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Logo Header ──────────────────────────────────────────────────────────────

@Composable
fun TBLogoHeader(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "TemuBelajar",
            fontSize = 38.sp,
            fontWeight = FontWeight.Black,
            color = TBColors.Primary,
            letterSpacing = (-1).sp
        )
        val isDark = isSystemInDarkTheme()
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = if (isSystemInDarkTheme()) TBColors.TextSecondaryDark else TBColors.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ─── Success Banner ───────────────────────────────────────────────────────────

@Composable
fun TBSuccessBanner(message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TBColors.Success.copy(alpha = 0.12f))
                .border(1.dp, TBColors.Success.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = TBColors.Success,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
// ─── Lottie Animation ──────────────────────────────────────────────────────────

@OptIn(ExperimentalResourceApi::class)
@Composable
fun TBLottie(
    resPath: String,
    modifier: Modifier = Modifier,
    iterations: Int = 1,
    isPlaying: Boolean = true,
    speed: Float = 1f,
    restartOnPlay: Boolean = true
) {
    var rawBytes by remember { mutableStateOf<ByteArray?>(null) }
    
    LaunchedEffect(resPath) {
        try {
            // The path might need to be adjusted based on the generated resource structure.
            // Based on the error "composeResources/temubelajar.core.generated.resources/files/cat_peek.json"
            // we should try the relative path directly as Res.readBytes handles the mapping.
            rawBytes = Res.readBytes(resPath)
        } catch (e: Exception) {
            println("Lottie load error: ${e.message} for path: $resPath")
        }
    }

    val composition = rememberLottieComposition {
        LottieCompositionSpec.JsonString(rawBytes?.decodeToString() ?: "")
    }
    
    Image(
        painter = rememberLottiePainter(
            composition = composition.value,
            iterations = if (iterations == Int.MAX_VALUE) Compottie.IterateForever else iterations,
            isPlaying = isPlaying && rawBytes != null,
            speed = speed
        ),
        contentDescription = null,
        modifier = modifier
    )
}
