package com.racha.ai.core.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppChooserDialog(
    state: AppChooserState,
    onAppSelected: (AppResolver.InstalledApp) -> Unit,
    onDismiss: () -> Unit
) {
    if (!state.isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (state.apps.isEmpty()) {
                Text("لا توجد تطبيقات متاحة.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            onClick = { onAppSelected(app) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

@Composable
fun AppItem(
    app: AppResolver.InstalledApp,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getEmojiForApp(app.packageName),
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getEmojiForApp(packageName: String): String {
    return when {
        packageName.contains("earth") -> "🌍"
        packageName.contains("maps") -> "🗺️"
        packageName.contains("osmand") -> "🧭"
        packageName.contains("waze") -> "🚗"
        packageName.contains("whatsapp") -> "💬"
        packageName.contains("telegram") -> "✈️"
        packageName.contains("messenger") || packageName.contains("orca") -> "💌"
        packageName.contains("instagram") -> "📸"
        packageName.contains("discord") -> "🎮"
        packageName.contains("mms") || packageName.contains("messaging") -> "📱"
        packageName.contains("gmail") || packageName.contains("gm") -> "📧"
        packageName.contains("outlook") -> "📨"
        packageName.contains("dialer") || packageName.contains("phone") -> "📞"
        packageName.contains("camera") -> "📷"
        packageName.contains("chrome") -> "🌐"
        packageName.contains("firefox") -> "🦊"
        packageName.contains("brave") -> "🦁"
        packageName.contains("spotify") -> "🎵"
        packageName.contains("youtube") -> "📺"
        packageName.contains("vlc") -> "🎬"
        packageName.contains("keep") -> "📝"
        packageName.contains("calendar") -> "📅"
        else -> "📱"
    }
}
