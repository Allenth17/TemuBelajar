package com.hiralen.temubelajar.app

import kotlinx.serialization.Serializable

sealed interface Destination {

    @Serializable data object
    AuthGraph          : Destination
    @Serializable data object
    LoginPage          : Destination
    @Serializable data object
    RegisterPage       : Destination
    @Serializable data object
    ForgotPasswordPage : Destination
    @Serializable data object
    OTPPage            : Destination

    @Serializable data object
    MainGraph   : Destination
    @Serializable data object
    HomePage    : Destination
    @Serializable data object
    ProfilePage : Destination


}