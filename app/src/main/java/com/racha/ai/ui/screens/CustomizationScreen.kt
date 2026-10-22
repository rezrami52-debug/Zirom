package com.racha.ai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.theme.AppThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    themeManager: AppThemeManager,
    onBackClick: () -> Unit
) {
    val themeState by themeManager.themeState.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { themeManager.saveBackgroundImage(it) }
    }

    val userColors = listOf(Color(0xFF00A3FF), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFFF44336))
    val rachaColors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0), Color(0xFFBAE6FD), Color(0xFFDCFCE7))
    val glowColors = listOf(Color(0xFF00A3FF), Color(0xFFD946EF), Color(0xFF00E5FF), Color(0xFFFFD600))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التخصيص 🎨", fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. صورة الخلفية
            Text("صورة خلفية التطبيق:", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("اختر صورة من ملفاتك")
                }
                if (themeState.customBackgroundPath != null) {
                    OutlinedButton(
                        onClick = { themeManager.clearBackgroundImage() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "حذف")
                    }
                }
            }

            Divider()

            // 2. لون فقاعة المستخدم
            Text("لون فقاعتك:", style = MaterialTheme.typography.titleMedium)
            ColorRow(
                colors = userColors,
                selectedColor = themeState.userBubbleColor,
                onColorSelected = { themeManager.updateUserBubbleColor(it) }
            )

            // 3. لون فقاعة Racha
            Text("لون فقاعة Racha:", style = MaterialTheme.typography.titleMedium)
            ColorRow(
                colors = rachaColors,
                selectedColor = themeState.rachaBubbleColor,
                onColorSelected = { themeManager.updateRachaBubbleColor(it) }
            )

            // 4. لون التوهج
            Text("لون التوهج:", style = MaterialTheme.typography.titleMedium)
            ColorRow(
                colors = glowColors,
                selectedColor = themeState.glowColor,
                onColorSelected = { themeManager.updateGlowColor(it) }
            )

            // 5. الشفافية
            Text("شفافية العناصر: ${(themeState.elementsAlpha * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = themeState.elementsAlpha,
                onValueChange = { themeManager.updateAlpha(it) },
                valueRange = 0.3f..1.0f
            )

            // 6. شدة التوهج
            Text("شدة التوهج: ${String.format("%.1f", themeState.glowIntensity)}x", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = themeState.glowIntensity,
                onValueChange = { themeManager.updateGlowIntensity(it) },
                valueRange = 0.1f..1.5f
            )

            Text(
                "💡 التغييرات تُحفظ وتُطبق تلقائياً على كامل التطبيق",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
fun ColorRow(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(colors) { color ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (color == selectedColor) 3.dp else 1.dp,
                        color = if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.LightGray,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center
            ) {
                if (color == selectedColor) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = if (color == Color.White) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
