package com.racha.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ModernPermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    isOptional: Boolean = false,
    icon: @Composable () -> Unit = {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
    },
    onActionClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    
    val cardColor = if (isDark) Color(0xFF131A29) else Color.White
    val borderColor = if (isDark) Color(0x1FFFFFFF) else Color(0xFFE0E0E0)
    val iconBgColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE3F2FD)
    val titleColor = if (isDark) Color.White else Color(0xFF0A1B3A)
    val descColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    
    val buttonColor = if (isGranted) {
        if (isDark) Color(0xFF1E293B) else Color(0xFFE0E0E0)
    } else {
        if (isDark) Color(0xFF00E5FF) else Color(0xFF0EA5E9)
    }
    val buttonContentColor = if (isGranted) {
        if (isDark) Color(0xFF00E5FF) else Color(0xFF0EA5E9)
    } else {
        if (isDark) Color(0xFF090D16) else Color.White
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(color = titleColor, fontSize = 17.sp))
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = description, style = MaterialTheme.typography.bodySmall.copy(color = descColor, fontSize = 12.sp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = buttonContentColor),
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isGranted) "مفعل ✓" else if (isOptional) "لاحقاً" else "تفعيل",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                )
            }
        }
    }
}
