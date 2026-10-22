import java.io.FileOutputStream
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
package com.racha.ai.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.racha.ai.core.preferences.AppPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    // ✅ استخدام State لتحديث الواجهة فوراً
    var userBubbleColor by remember { mutableIntStateOf(prefs.getUserBubbleColor()) }
    var aiBubbleColor by remember { mutableIntStateOf(prefs.getAiBubbleColor()) }
    var glowColor by remember { mutableIntStateOf(prefs.getPrimaryGlowColor()) }
    var glassAlpha by remember { mutableFloatStateOf(prefs.getGlassAlpha()) }
    var glowIntensity by remember { mutableFloatStateOf(prefs.getGlowIntensity()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "custom_bg.jpg")
                val outputStream = FileOutputStream(file)
                inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
                prefs.setBackgroundImagePath(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎨 التخصيص", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // لون فقاعة المستخدم
            Text("لون فقاعتك:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            ColorRow(
                colors = listOf(0xFF0EA5E9.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF9C27B0.toInt(), 0xFFF44336.toInt()),
                selected = userBubbleColor,
                onSelect = { userBubbleColor = it; prefs.setUserBubbleColor(it) }
            )

            // لون فقاعة Racha
            Text("لون فقاعة Racha:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            ColorRow(
                colors = listOf(0xFFFFFFFF.toInt(), 0xFFE0E0E0.toInt(), 0xFFBBDEFB.toInt(), 0xFFC8E6C9.toInt()),
                selected = aiBubbleColor,
                onSelect = { aiBubbleColor = it; prefs.setAiBubbleColor(it) }
            )

            // لون التوهج
            Text("لون التوهج:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            ColorRow(
                colors = listOf(0xFF0EA5E9.toInt(), 0xFFBD4CFF.toInt(), 0xFF00E5FF.toInt(), 0xFFFFD700.toInt()),
                selected = glowColor,
                onSelect = { glowColor = it; prefs.setPrimaryGlowColor(it) }
            )

            // شفافية العناصر
            Text("شفافية العناصر: ${(glassAlpha * 100).toInt()}%", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = glassAlpha,
                onValueChange = { glassAlpha = it; prefs.setGlassAlpha(it) },
                valueRange = 0.1f..1f
            )

            // شدة التوهج
            Text("شدة التوهج: ${String.format("%.1f", glowIntensity)}x", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Slider(
                value = glowIntensity,
                onValueChange = { glowIntensity = it; prefs.setGlowIntensity(it) },
                valueRange = 0f..2f
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text("💡 التغييرات تُحفظ تلقائياً", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ColorRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { colorInt ->
            val color = Color(colorInt)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = 3.dp,
                        color = if (selected == colorInt) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onSelect(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (selected == colorInt) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = if (colorInt == 0xFFFFFFFF.toInt()) Color.Black else Color.White
                    )
                }
            }
        }
    }
}
