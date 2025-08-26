package com.hiralen.temubelajar.auth.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OtpTextField(
    otpText: String,
    onOtpTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = otpText,
        onValueChange = { updatedOtp ->
            if (updatedOtp.length <= 6) {
                onOtpTextChange(updatedOtp)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = modifier,
        decorationBox = {
            Row(horizontalArrangement = Arrangement.Center) {
                repeat(6) { index ->
                    val char = when {
                        index >= otpText.length -> ""
                        else -> otpText[index].toString()
                    }
                    val isFocused = index == otpText.length

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .border(
                                width = 2.dp,
                                color = if (isFocused) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
        }
    )
}