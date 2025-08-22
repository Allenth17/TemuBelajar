package com.hiralen.temubelajar.auth.presentation.otp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiralen.temubelajar.auth.presentation.AuthViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hiralen.temubelajar.auth.presentation.components.HeaderText
import com.hiralen.temubelajar.auth.presentation.components.OtpTextField
import com.hiralen.temubelajar.core.presentation.defaultPadding

@Composable
fun OTPScreen(
    viewModel: AuthViewModel,
    onVerificationSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val otpState by viewModel.otpState.collectAsStateWithLifecycle()
    val otp = rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val email by viewModel.emailState.collectAsStateWithLifecycle()

    LaunchedEffect(
        otp.value
    ) {
        viewModel.updateOtp(otp.value)
    }

    LaunchedEffect(otpState) {
        when (val state = otpState) {
            is OTPState.Success -> {
                onVerificationSuccess()
                focusManager.clearFocus()
                otp.value = ""

                viewModel.resetOtpState()
            }

            is OTPState.Error -> {
                val error = state.message
                viewModel.resetOtpState()
            }

            else -> {

            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(defaultPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HeaderText(
            text = "OTP Verification",
            modifier = modifier.padding(vertical = defaultPadding)
        )

        Text(
            text = "Kode OTP telah dikirim ke:",
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = email,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .padding(bottom = 32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OtpTextField(
                otpText = otp.value,
                onOtpTextChange = { updatedOtp ->
                    otp.value = updatedOtp
                },
                modifier = Modifier.weight(0.7f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            TextButton(
                onClick = { /* Handle resend OTP */ },
                modifier = Modifier.weight(0.3f)
            ) {
                Text(
                    "Resend OTP",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.verifyOtp()
                focusManager.clearFocus()
            },
            modifier = modifier.fillMaxWidth(),
            enabled = otp.value.length == 6
        ) {
            if (otpState is OTPState.Loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Verify OTP")
            }
        }
    }
}