package com.hiralen.temubelajar.auth.data.mappers

import com.hiralen.temubelajar.auth.data.dto.AccountDto
import com.hiralen.temubelajar.auth.data.dto.LoginResponseDto
import com.hiralen.temubelajar.auth.data.dto.MessageDto
import com.hiralen.temubelajar.auth.domain.Account
import com.hiralen.temubelajar.auth.domain.LoginResponse
import com.hiralen.temubelajar.auth.domain.Message

fun AccountDto.toAccount(): Account {
    return Account(
        email = email,
        username = username,
        name = name,
        phone = phone,
        university = university
    )
}

fun Account.toAccountDto() : AccountDto {
    return AccountDto(
        email = email,
        username = username,
        name = name,
        phone = phone,
        university = university
    )
}

fun MessageDto.toMessage(): Message {
    return Message(
        message = message
    )
}

fun Message.toMessageDto() : MessageDto {
    return MessageDto(
        message = message
    )
}

fun LoginResponse.toLoginDto(): LoginResponseDto {
    return LoginResponseDto(
        token = token
    )
}

fun LoginResponseDto.toLoginResponse(): LoginResponse {
    return LoginResponse(
        token = token
    )
}