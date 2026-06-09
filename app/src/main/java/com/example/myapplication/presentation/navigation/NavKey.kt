package com.example.myapplication.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey {
    @Serializable
    data object Chat : NavKey

    @Serializable
    data object Settings : NavKey
}
