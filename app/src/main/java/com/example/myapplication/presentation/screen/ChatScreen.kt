package com.example.myapplication.presentation.screen

import android.content.ClipData
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.FileAttachment
import com.example.myapplication.domain.model.MessageRole
import com.example.myapplication.domain.model.ModelInfo
import com.example.myapplication.domain.pricing.Pricing
import com.example.myapplication.presentation.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            for (uri in uris) {
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val fileName = queryFileName(context, uri) ?: "file"
                viewModel.addAttachment(FileAttachment(
                    uri = uri.toString(),
                    fileName = fileName,
                    mimeType = mimeType,
                ))
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LLM Chat") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Настройки")
                    }
                    TextButton(
                        onClick = { viewModel.clearChat() },
                        enabled = uiState.isConfigured
                    ) { Text("Очистить") }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
        ) {
            if (!uiState.isConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Параметры base_url и/или api_key не заданы в local.properties.\n" +
                                "Добавьте:\nbase_url=https://your-api-url/\napi_key=your-api-key",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            ModelSelector(
                models = uiState.models,
                selectedModel = uiState.selectedModel,
                isLoading = uiState.isModelsLoading,
                enabled = uiState.isConfigured,
                onModelSelected = { viewModel.onSelectedModelChanged(it) },
                onRefresh = { viewModel.loadModels() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Messages
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (uiState.messages.isEmpty() && !uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.isConfigured) "Начните диалог" else "Настройте параметры",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    val totalItems = uiState.messages.size + (if (uiState.isLoading) 1 else 0)

                    LaunchedEffect(uiState.streamingText, uiState.messages.size) {
                        if (totalItems > 0) listState.animateScrollToItem(totalItems - 1)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.messages) { message -> MessageBubble(message = message) }
                        if (uiState.isLoading && uiState.streamingText.isNotBlank()) {
                            item { StreamingBubble(text = uiState.streamingText) }
                        } else if (uiState.isLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                Text(text = error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            if (uiState.totalTokens > 0) {
                TokenStatsPanel(
                    totalPromptTokens = uiState.totalPromptTokens,
                    totalCompletionTokens = uiState.totalCompletionTokens,
                    totalTokens = uiState.totalTokens,
                    estimatedCost = uiState.estimatedCost,
                    isStreaming = uiState.generationConfig.useStreaming,
                )
            }

            // Pending attachment previews
            if (uiState.pendingAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(uiState.pendingAttachments) { index, attachment ->
                        AttachmentPreview(attachment = attachment, onRemove = { viewModel.removeAttachment(index) })
                    }
                }
            }

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                IconButton(
                    onClick = { filePickerLauncher.launch(arrayOf("image/*", "text/*", "application/json", "application/pdf")) },
                    enabled = uiState.isConfigured && !uiState.isLoading,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Прикрепить файл")
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onInputTextChanged(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите сообщение...") },
                    maxLines = 4,
                    enabled = uiState.isConfigured && !uiState.isLoading,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.sendMessage() },
                    enabled = uiState.isConfigured &&
                            (uiState.inputText.isNotBlank() || uiState.pendingAttachments.isNotEmpty()) &&
                            uiState.selectedModel.isNotEmpty() && !uiState.isLoading,
                    modifier = Modifier.padding(bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("➤") }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(attachment: FileAttachment, onRemove: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(modifier = Modifier.size(width = 100.dp, height = 80.dp)) {
            if (attachment.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = Uri.parse(attachment.uri),
                    contentDescription = attachment.fileName,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "📄", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Удалить",
                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MessageAttachmentPreview(attachment: FileAttachment) {
    if (attachment.mimeType.startsWith("image/")) {
        AsyncImage(
            model = Uri.parse(attachment.uri),
            contentDescription = attachment.fileName,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
    } else {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📄", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = attachment.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) it.getString(nameIndex) else null
        } else null
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = "Ассистент ✨", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            MarkdownText(markdown = text)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    models: List<ModelInfo>, selectedModel: String, isLoading: Boolean, enabled: Boolean,
    onModelSelected: (String) -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = selectedModel, onValueChange = {}, readOnly = true, enabled = enabled,
                label = { Text("Модель") },
                trailingIcon = { if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                models.forEach { model ->
                    DropdownMenuItem(text = { Text(model.id) }, onClick = { onModelSelected(model.id); expanded = false })
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onRefresh, enabled = enabled) { Text("↻") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
            }
        ),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (isUser) 4.dp else 16.dp, bottomEnd = if (isUser) 16.dp else 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = when (message.role) {
                    MessageRole.USER -> "Вы"
                    MessageRole.ASSISTANT -> "Ассистент"
                    MessageRole.SYSTEM -> "Система"
                },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            for (attachment in message.attachments) {
                MessageAttachmentPreview(attachment = attachment)
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (message.role == MessageRole.ASSISTANT) {
                MarkdownText(markdown = message.content)
                if (message.usage != null) TokenUsageFooter(message)
            } else {
                if (message.content.isNotBlank()) {
                    Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
                }
                val estimatedTokens = message.content.length / 4
                if (estimatedTokens > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "~$estimatedTokens токенов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TokenUsageFooter(message: ChatMessage) {
    val usage = message.usage ?: return
    val cost = message.model?.let { Pricing.calculateCost(it, usage) }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = buildString {
            append("\uD83C\uDFAF "); append(usage.promptTokens); append(" → "); append(usage.completionTokens); append(" токенов")
            if (usage.cachedTokens > 0) { append(" | Кеш: "); append(usage.cachedTokens) }
            if (cost != null) { append(" | $"); append(String.format("%.5f", cost)) }
        },
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TokenStatsPanel(
    totalPromptTokens: Int, totalCompletionTokens: Int, totalTokens: Int, estimatedCost: Double, isStreaming: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📊 $totalTokens токенов (вх: $totalPromptTokens | вых: $totalCompletionTokens)",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Text(
                text = "${if (isStreaming) "⚡ Streaming" else "📝 Sync"} | \$${String.format("%.5f", estimatedCost)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
