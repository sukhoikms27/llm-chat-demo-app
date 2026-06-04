package com.example.myapplication.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.myapplication.ui.screen.ChatScreen
import com.example.myapplication.ui.screen.SettingsScreen
import com.example.myapplication.ui.viewmodel.ChatViewModel

@Composable
fun AppNavigation(viewModel: ChatViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isSettingsOpen = uiState.isSettingsOpen

    if (isSettingsOpen) {
        SettingsScreen(
            currentConfig = uiState.generationConfig,
            onSave = { config ->
                viewModel.onGenerationConfigChanged(config)
                viewModel.setSettingsOpen(false)
            },
            onBack = { viewModel.setSettingsOpen(false) }
        )
    } else {
        ChatScreen(
            viewModel = viewModel,
            onNavigateToSettings = { viewModel.setSettingsOpen(true) }
        )
    }
}
