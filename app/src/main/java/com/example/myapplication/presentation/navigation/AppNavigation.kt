package com.example.myapplication.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.example.myapplication.presentation.screen.ChatScreen
import com.example.myapplication.presentation.screen.SettingsScreen
import com.example.myapplication.presentation.screen.StatsScreen
import com.example.myapplication.domain.model.MessageRole
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
                    onNavigateToStats = { navigator.goTo(NavKey.Stats) },
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
            entry<NavKey.Stats> {
                val state = viewModel.uiState.value
                StatsScreen(
                    totalPromptTokens = state.totalPromptTokens,
                    totalCompletionTokens = state.totalCompletionTokens,
                    totalTokens = state.totalTokens,
                    estimatedCost = state.estimatedCost,
                    messageCount = state.messages.count { it.role == MessageRole.ASSISTANT },
                    tokensSaved = state.tokensSaved,
                    hasActiveSummary = state.hasActiveSummary,
                    isStreaming = state.generationConfig.useStreaming,
                    onBack = { navigator.goBack() },
                )
            }
        }
    )
}
