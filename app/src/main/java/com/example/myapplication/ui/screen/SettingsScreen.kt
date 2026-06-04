package com.example.myapplication.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.models.GenerationConfig
import com.example.myapplication.data.models.GenerationPresets
import com.example.myapplication.data.models.presetLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: GenerationConfig,
    onSave: (GenerationConfig) -> Unit,
    onBack: () -> Unit
) {
    var config by remember(currentConfig) { mutableStateOf(currentConfig) }

    // Текстовые поля для тонкой настройки (извлекаем из config)
    var temperatureText by remember(config.temperature) {
        mutableStateOf(config.temperature.toString())
    }
    var topPText by remember(config.topP) {
        mutableStateOf(config.topP.toString())
    }
    var maxTokensText by remember(config.maxTokens) {
        mutableStateOf(config.maxTokens.toString())
    }
    var minTokensText by remember(config.minTokens) {
        mutableStateOf(config.minTokens.toString())
    }
    var stopText by remember(config.stop) {
        mutableStateOf(config.stop?.joinToString(", ") ?: "")
    }
    var systemPromptText by remember(config.systemPrompt) {
        mutableStateOf(config.systemPrompt ?: "")
    }

    fun buildConfigFromFields(): GenerationConfig = config.copy(
        temperature = temperatureText.toDoubleOrNull() ?: config.temperature,
        topP = topPText.toDoubleOrNull() ?: config.topP,
        maxTokens = maxTokensText.toIntOrNull() ?: config.maxTokens,
        minTokens = minTokensText.toIntOrNull() ?: config.minTokens,
        stop = stopText.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
        systemPrompt = systemPromptText.takeIf { it.isNotBlank() }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки генерации") }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            // Скроллируемый контент с нижним отступом под кнопку
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 72.dp) // отступ под кнопку (48dp высота + 16dp*2 padding)
            ) {
                // Пресеты
                Text(
                    text = "Пресеты",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                val presets = GenerationPresets.all
                val selectedIndex = presets.indexOfFirst { it == config }.coerceAtLeast(0)

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEachIndexed { index, preset ->
                        SegmentedButton(
                            selected = index == selectedIndex,
                            onClick = {
                                config = preset
                                // Обновляем текстовые поля из пресета
                                temperatureText = preset.temperature.toString()
                                topPText = preset.topP.toString()
                                maxTokensText = preset.maxTokens.toString()
                                minTokensText = preset.minTokens.toString()
                                stopText = preset.stop?.joinToString(", ") ?: ""
                                systemPromptText = preset.systemPrompt ?: ""
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = presets.size)
                        ) {
                            Text(preset.presetLabel)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Тонкая настройка
                Text(
                    text = "Параметры",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Temperature
                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    label = { Text("Temperature") },
                    supportingText = { Text("0.0 — детерминированно, 2.0 — хаотично") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Top P
                OutlinedTextField(
                    value = topPText,
                    onValueChange = { topPText = it },
                    label = { Text("Top P") },
                    supportingText = { Text("Nucleus sampling: 0.1 — сужает, 1.0 — всё разрешено") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Max tokens
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it },
                    label = { Text("Max Tokens") },
                    supportingText = { Text("Максимальное количество токенов в ответе") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Min tokens
                OutlinedTextField(
                    value = minTokensText,
                    onValueChange = { minTokensText = it },
                    label = { Text("Min Tokens") },
                    supportingText = { Text("Минимальное количество токенов в ответе") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stop sequences
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it },
                    label = { Text("Stop Sequences") },
                    supportingText = { Text("Стоп-слова через запятую, напр.: \\n\\n, END") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // System prompt
                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = { systemPromptText = it },
                    label = { Text("System Prompt") },
                    supportingText = { Text("Системный промпт для модели") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
            }

            // Кнопка, прибитая к низу экрана
            Button(
                onClick = {
                    onSave(buildConfigFromFields())
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Сохранить и вернуться")
            }
        }
    }
}
