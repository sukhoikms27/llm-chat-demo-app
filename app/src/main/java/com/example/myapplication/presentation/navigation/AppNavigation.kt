package com.example.myapplication.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.myapplication.presentation.screen.ChatScreen
import com.example.myapplication.presentation.screen.SettingsScreen
import com.example.myapplication.presentation.viewmodel.ChatViewModel

@Composable
fun AppNavigation(
    navigator: Navigator,
    viewModel: ChatViewModel,
) {
    NavDisplay(
        backStack = navigator.backStack,
        onBack = { navigator.goBack() },
        entryProvider = entryProvider {
            entry<NavKey.Chat> {
                ChatScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navigator.goTo(NavKey.Settings) },
                )
            }
            entry<NavKey.Settings> {
                SettingsScreen(
                    currentConfig = viewModel.uiState.value.generationConfig,
                    onSave = { config ->
                        viewModel.onGenerationConfigChanged(config)
                        navigator.goBack()
                    },
                    onBack = { navigator.goBack() },
                )
            }
        }
    )
}
