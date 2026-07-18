package com.hiralen.temubelajar.auth.presentation.otp

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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

    // Phase 5.27 — one FocusRequester per box so we can auto-advance the
    // caret after each keystroke and jump back on Backspace.
    val focusRequesters = remember { List(6) { FocusRequester() } }

    // Auto-submit once the 6th digit lands. Kept in a LaunchedEffect so the
    // call only fires when the code actually transitions to length 6, not on
    // every recomposition.
    LaunchedEffect(state.otp) {
        if (state.otp.length == 6 && !state.isLoading) {
            component.verify()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LinearColors.Canvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Phase 5.26 — IME / status-bar insets so the keyboard never
                // covers the OTP grid and the layout does not draw under the
                // system status bar under edge-to-edge.
                .imePadding()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TBSpace.LG),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TBCard(
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .padding(vertical = TBSpace.XL)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(TBShapes.LG)
                            .background(LinearColors.Surface2)
                            .border(1.dp, LinearColors.Hairline, TBShapes.LG),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            TablerIcons.ShieldCheck,
                            contentDescription = null,
                            tint = LinearColors.Primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(Modifier.height(TBSpace.LG))

                    Text(
                        "Verify your email",
                        style = TBTypography.Headline,
                        color = LinearColors.Ink
                    )
                    Spacer(Modifier.height(TBSpace.SM))
                    Text(
                        "We've sent a 6-digit code to\n${component.email}",
                        style = TBTypography.BodySM,
                        color = LinearColors.InkMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(TBSpace.XL))

                    // Phase 5.27 — six boxed cells with auto-advance + backspace.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TBSpace.SM, Alignment.CenterHorizontally)
                    ) {
                        for (index in 0 until 6) {
                            OtpCell(
                                value = state.otp.getOrNull(index)?.toString() ?: "",
                                index = index,
                                focusRequester = focusRequesters[index],
                                onDigitEntered = { digit ->
                                    // Place this digit at position `index`, replacing
                                    // whatever was there. Spaces pad leading gaps so
                                    // `OTPComponent.onOtpChange` keeps the rest intact
                                    // once it filters to digits.
                                    val current = state.otp
                                    val sanitised = if (current.length > index) {
                                        current.substring(0, index) + digit + current.substring(index + 1)
                                    } else {
                                        val pad = " ".repeat(index - current.length)
                                        current + pad + digit
                                    }
                                    component.onOtpChange(sanitised)
                                    // Auto-advance to the next cell. Skipping on the
                                    // last cell — the LaunchedEffect above fires
                                    // `component.verify()` when length == 6.
                                    if (index < 5) {
                                        focusRequesters[index + 1].requestFocus()
                                    }
                                },
                                onDigitDeleted = {
                                    // User backspaced this cell's digit (it went from
                                    // populated to empty). Strip the digit at `index`
                                    // out of the OTP string so the rest of the boxes
                                    // re-render, then keep the caret here so a second
                                    // Backspace falls through into the previous box via
                                    // `onBackspaceEmpty`.
                                    val current = state.otp
                                    if (current.length > index) {
                                        val newOtp = current.substring(0, index) + current.substring(index + 1)
                                        component.onOtpChange(newOtp)
                                    }
                                },
                                onBackspaceEmpty = {
                                    // Backspace was pressed on an empty cell —
                                    // jump the caret to the previous box so the
                                    // user can keep deleting backwards.
                                    if (index > 0) {
                                        focusRequesters[index - 1].requestFocus()
                                    }
                                }
                            )
                        }
                    }

                    if (state.error != null) {
                        Spacer(Modifier.height(TBSpace.MD))
                        TBErrorBanner(state.error!!)
                    }

                    if (state.successMessage != null) {
                        Spacer(Modifier.height(TBSpace.MD))
                        TBSuccessBanner(state.successMessage!!)
                    }

                    Spacer(Modifier.height(TBSpace.XL))

                    TBPrimaryButton(
                        text = "Verify code",
                        onClick = component::verify,
                        isLoading = state.isLoading,
                        enabled = state.otp.length == 6,
                        icon = TablerIcons.ArrowRight
                    )

                    Spacer(Modifier.height(TBSpace.LG))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Didn't receive code? ",
                            color = LinearColors.InkMuted,
                            style = TBTypography.BodySM
                        )
                        if (state.isResending) {
                            CircularProgressIndicator(
                                color = LinearColors.Primary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                // Phase 5.37 — match ID elsewhere in the auth flow.
                                "Kirim ulang",
                                color = LinearColors.PrimaryHover,
                                style = TBTypography.BodySM.copy(fontWeight = FontWeight.Medium),
                                modifier = Modifier.clickableRole(onClick = component::resend)
                            )
                        }
                    }
                }
            }

            Text(
                "Check your spam folder if you haven't received the email.",
                style = TBTypography.Caption,
                color = LinearColors.InkTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TBSpace.MD)
            )
        }
    }
}

/**
 * Phase 5.27 — single OTP digit cell. A 1-char OutlinedTextField whose
 * container grows to a square cell. Auto-advances on digit entry (handled by
 * the caller via [focusRequester]); jumps to the previous cell when Backspace
 * is pressed while this cell is already empty.
 */
@Composable
private fun OtpCell(
    value: String,
    index: Int,
    focusRequester: FocusRequester,
    onDigitEntered: (Char) -> Unit,
    onDigitDeleted: () -> Unit,
    onBackspaceEmpty: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val digit = newValue.lastOrNull { it.isDigit() }
            if (digit != null) {
                // A digit was typed or pasted (type-over). Forward it.
                onDigitEntered(digit)
            } else if (newValue.isEmpty() && value.isNotEmpty()) {
                // User deleted the only digit in this box via Backspace —
                // notify the parent so the OTP string can be updated. Note
                // that this branch fires for "delete the displayed digit";
                // the case of Backspace on an empty cell is handled by the
                // `onKeyEvent` modifier below (jump-back-to-previous).
                onDigitDeleted()
            }
            // Else: empty value → empty input (no-op) on a cell that was
            // already empty; the onKeyEvent below will handle the back-jump.
        },
        modifier = Modifier
            .size(width = 48.dp, height = 56.dp)
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    event.key == Key.Backspace &&
                    value.isEmpty()
                ) {
                    onBackspaceEmpty()
                    true
                } else {
                    false
                }
            },
        textStyle = TBTypography.DisplayMD.copy(
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = LinearColors.Ink
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LinearColors.PrimaryFocus,
            unfocusedBorderColor = LinearColors.Hairline,
            focusedContainerColor = LinearColors.Surface1,
            unfocusedContainerColor = LinearColors.Surface1,
            cursorColor = LinearColors.Primary
        ),
        shape = TBShapes.LG,
        singleLine = true
    )
}
