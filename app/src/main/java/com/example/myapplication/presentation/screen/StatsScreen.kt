package com.example.myapplication.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    totalPromptTokens: Int,
    totalCompletionTokens: Int,
    totalTokens: Int,
    estimatedCost: Double,
    messageCount: Int,
    tokensSaved: Int,
    hasActiveSummary: Boolean,
    isStreaming: Boolean,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // --- Токены ---
            SectionHeader(text = "Токены")
            StatRow(label = "Всего", value = formatNum(totalTokens))
            StatRow(label = "Входящие", value = formatNum(totalPromptTokens))
            StatRow(label = "Исходящие", value = formatNum(totalCompletionTokens))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Экономия контекста ---
            SectionHeader(text = "Экономия контекста")
            StatRow(label = "Сэкономлено токенов", value = formatNum(tokensSaved))
            StatRow(label = "Контекст сжат", value = if (hasActiveSummary) "✓ Активен" else "—")

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Стоимость ---
            SectionHeader(text = "Стоимость")
            StatRow(label = "Оценка", value = "$${String.format("%.5f", estimatedCost)}")
            StatRow(label = "Сообщений", value = formatNum(messageCount))

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // --- Режим ---
            SectionHeader(text = "Режим")
            StatRow(label = "Тип", value = if (isStreaming) "⚡ Streaming" else "📝 Sync")
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatNum(n: Int): String = "%,d".format(n)
