package com.hiralen.temubelajar.core.ui

import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.Compottie
import org.jetbrains.compose.resources.*
import temubelajar.composeapp.generated.resources.Res
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState

/**
 * Linear-flavored reusable components.
 *
 * Design system contract:
 *   - Primary CTA: lavender #5e6ad2 on surface-1's container text (#fff), 8px corners,
 *     hover #828fff, focus ring 2px #5e69d1 @ 50%.
 *   - Secondary CTA: surface-1 fill, hairline border, ink text.
 *   - Cards: surface-1 fill, 1px hairline, 12px corners, no shadow.
 *   - Inputs: surface-1 fill, hairline border, 8px corners, lavender focus ring.
 *   - Banners: surface-2 fill, hairline ring, 12px corners, no shadow.
 *
 * For convenience the primary button sweeps through Linear colors directly rather
 * than going through the back-compat `TBColors` aliases.
 */

@Composable
fun TBPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    containerColor: Color = LinearColors.Primary,
    icon: ImageVector? = null
) {
    val interaction =remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val bg = when {
        !enabled || isLoading -> LinearColors.Primary.copy(alpha = 0.4f)
        pressed -> LinearColors.PrimaryFocus
        hovered -> LinearColors.PrimaryHover
        else -> containerColor
    }

    // Phase 5.36 — wrap onClick with a light haptic tick. Primary CTAs
    // are the highest-signal touch sites (Sign in, Mulai mencari, Daftar,
    // Send report, End call) so the click should register to the
    // fingertips/ear. Disabled/loading clicks early-out before vibrate.
    val onClickHaptic: () -> Unit = {
        if (enabled && !isLoading) platformHapticClick()
        onClick()
    }

    Button(
        onClick = onClickHaptic,
        enabled = enabled && !isLoading,
        interactionSource = interaction,
        modifier = modifier
            .height(TBSpace.ButtonHeight)
            .fillMaxWidth()
            .semantics {
                // Material3: explicitly mark the node as disabled so Talkback /
                // VoiceOver announce "disabled" instead of just falling through
                // to colour contrast. Propagates the @Composable `enabled=false`
                // state to the accessibility tree. Phase 6.8.
                if (!enabled || isLoading) this.disabled()
            },
        shape = TBShapes.MD,
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = LinearColors.InverseCanvas,
            disabledContainerColor = LinearColors.Primary.copy(alpha = 0.4f),
            disabledContentColor = LinearColors.InkTertiary
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = LinearColors.InverseCanvas,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (icon != null) {
                    Icon(icon, null, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = text,
                    style = TBTypography.Button
                )
            }
        }
    }
}

/**
 * Secondary CTA — surface-1 fill with hairline border and ink text. Used for
 * "Sign in" / "Read changelog" type actions that pair with a primary CTA.
 */
@Composable
fun TBSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(TBSpace.ButtonHeight)
            .fillMaxWidth(),
        shape = TBShapes.MD,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LinearColors.Surface1,
            contentColor = LinearColors.Ink,
            disabledContainerColor = LinearColors.Surface1.copy(alpha = 0.5f),
            disabledContentColor = LinearColors.InkTertiary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, LinearColors.Hairline),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(16.dp))
            }
            Text(text = text, style = TBTypography.Button)
        }
    }
}

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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = TBTypography.BodySM.copy(fontWeight = FontWeight.Medium),
            color = LinearColors.InkMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = LinearColors.InkTertiary, style = TBTypography.Body)
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        icon,
                        null,
                        tint = LinearColors.InkSubtle,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            isError = isError,
            singleLine = singleLine,
            shape = TBShapes.MD,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LinearColors.PrimaryFocus,
                unfocusedBorderColor = LinearColors.Hairline,
                errorBorderColor = LinearColors.Error,
                focusedTextColor = LinearColors.Ink,
                unfocusedTextColor = LinearColors.Ink,
                focusedContainerColor = LinearColors.Surface1,
                unfocusedContainerColor = LinearColors.Surface1,
                cursorColor = LinearColors.Primary
            )
        )
    }
}

/**
 * Linear card — surface-1 fill on canvas, 1px hairline, 12px corners, zero elevation.
 */
@Composable
fun TBCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(TBShapes.LG)
            .background(LinearColors.Surface1)
            .border(1.dp, LinearColors.Hairline, TBShapes.LG)
            .padding(TBSpace.LG),
        content = content
    )
}

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
                .clip(TBShapes.LG)
                .background(LinearColors.Surface2)
                .border(1.dp, LinearColors.Error.copy(alpha = 0.35f), TBShapes.LG)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = LinearColors.Error,
                style = TBTypography.BodySM,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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
                .clip(TBShapes.LG)
                .background(LinearColors.Surface2)
                .border(1.dp, LinearColors.Success.copy(alpha = 0.35f), TBShapes.LG)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = message,
                color = LinearColors.Success,
                style = TBTypography.BodySM,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

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

/**
 * Phase 5.30 — accessibility wrapper for hand-rolled `Modifier.clickable {…}`
 * sites. Plain clickables don't expose a `Role` to the accessibility tree, so
 * TalkBack/VoiceOver announce them as generic "tap to activate" with no hint
 * that they're buttons (often the right pattern, but our clickable sites
 * act as buttons: chat-overlay buttons, "report reason" chips, "go to login"
 * inline links). Wrap them with this so they announce as Button and provide
 * the `onClick` label to accessibility services. Drop-in replacement:
 *
 *   Modifier.clickable { onClick() }
 *   →
 *   Modifier.clickableRole { onClick() }
 *
 * (Defined as Composable-free `Modifier. extension` so callers don't need
 * to lift into a Composable scope.)
 */
fun Modifier.clickableRole(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this
    .clickable(enabled = enabled, onClick = onClick)
    .semantics {
        this.role = Role.Button
        // Implicit: clickable already sets onClickLabel; semantics() here
        // only adds the role. No override necessary.
    }
