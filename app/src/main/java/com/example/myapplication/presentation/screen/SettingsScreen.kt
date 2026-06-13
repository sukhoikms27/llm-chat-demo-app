package com.example.myapplication.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.example.myapplication.domain.model.ContextStrategyType
import com.example.myapplication.domain.model.GenerationConfig
import com.example.myapplication.domain.model.GenerationPresets
import com.example.myapplication.domain.model.presetLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentConfig: GenerationConfig,
    onSave: (GenerationConfig) -> Unit,
    onBack: () -> Unit
) {
    var config by remember(currentConfig) { mutableStateOf(currentConfig) }

    var temperatureText by remember(config.temperature) {
        mutableStateOf(config.temperature.toString())
    }
    var topPText by remember(config.topP) {
        mutableStateOf(config.topP.toString())
    }
    var maxTokensText by remember(config.maxTokens) {
        mutableStateOf(config.maxTokens?.toString().orEmpty())
    }
    var stopText by remember(config.stop) {
        mutableStateOf(config.stop?.joinToString(", ") ?: "")
    }
    var systemPromptText by remember(config.systemPrompt) {
        mutableStateOf(config.systemPrompt ?: "")
    }

    fun buildConfigFromFields(): GenerationConfig = config.copy(
        temperature = temperatureText.toDoubleOrNull(),
        topP = topPText.toDoubleOrNull(),
        maxTokens = maxTokensText.toIntOrNull() ?: config.maxTokens,
        stop = stopText.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() },
        systemPrompt = systemPromptText.takeIf { it.isNotBlank() },
        enableThinking = config.enableThinking,
    )

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки генерации") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .padding(bottom = 72.dp)
            ) {
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
                                temperatureText = preset.temperature?.toString() ?: ""
                                topPText = preset.topP?.toString() ?: ""
                                maxTokensText = preset.maxTokens?.toString() ?: ""
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

                // Streaming toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Streaming API",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (config.useStreaming) "Ответ появляется постепенно" else "Ответ появляется целиком",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = config.useStreaming,
                        onCheckedChange = { config = config.copy(useStreaming = it) },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thinking mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Thinking режим",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = if (config.enableThinking) "Модель рассуждает шаг за шагом" else "Обычный режим ответа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = config.enableThinking,
                        onCheckedChange = { config = config.copy(enableThinking = it) },
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Параметры",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = temperatureText,
                    onValueChange = { temperatureText = it },
                    label = { Text("Temperature") },
                    supportingText = { Text("0.0 — 1.0 (по умолчанию 1.0)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = topPText,
                    onValueChange = { topPText = it },
                    label = { Text("Top P") },
                    supportingText = { Text("0.01 — 1.0 (по умолчанию 0.95)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { maxTokensText = it },
                    label = { Text("Max Tokens") },
                    supportingText = { Text("1 — 131072 (зависит от модели)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = stopText,
                    onValueChange = { stopText = it },
                    label = { Text("Stop Sequences") },
                    supportingText = { Text("Стоп-слово (пока поддерживается только одно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = { systemPromptText = it },
                    label = { Text("System Prompt") },
                    supportingText = { Text("Системный промпт для модели") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Context Management ---
                Text(
                    text = "Управление контекстом",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Strategy selector
                Text(
                    text = "Стратегия управления контекстом",
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(modifier = Modifier.height(8.dp))

                val strategies = ContextStrategyType.entries
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    strategies.forEachIndexed { index, strategy ->
                        SegmentedButton(
                            selected = config.contextStrategy == strategy,
                            onClick = { config = config.copy(contextStrategy = strategy) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = strategies.size
                            )
                        ) {
                            Text(strategy.displayName)
                        }
                    }
                }

                Text(
                    text = when (config.contextStrategy) {
                        ContextStrategyType.SLIDING_WINDOW -> "В запрос отправляются только последние N сообщений, остальное отбрасывается"
                        ContextStrategyType.SUMMARIZATION -> "Автоматическое суммирование старых сообщений"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Последние сообщения: ${config.recentMessageCount}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = config.recentMessageCount.toFloat(),
                    onValueChange = { config = config.copy(recentMessageCount = it.toInt()) },
                    valueRange = 2f..20f,
                    steps = 17,
                )
                Text(
                    text = "Сколько сообщений всегда передаются модели",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (config.contextStrategy == ContextStrategyType.SUMMARIZATION) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Интервал сжатия: ${config.summarizeInterval}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = config.summarizeInterval.toFloat(),
                        onValueChange = { config = config.copy(summarizeInterval = it.toInt()) },
                        valueRange = 2f..10f,
                        steps = 7,
                    )
                    Text(
                        text = "Сколько новых сообщений накапливается перед очередным сжатием",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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
