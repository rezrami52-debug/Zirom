package com.racha.ai.ui.screens.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.racha.ai.R
import com.racha.ai.core.voice.JarvisVoiceEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val isUser: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenPrayer: () -> Unit = {},
    onOpenRoutine: () -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage("أهلاً بك يا سيدي. أنا Racha AI، أعمل بكامل طاقتي واستباقيتي لتنفيذ أوامرك.", false)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F172A))
                                .border(1.5.dp, Color(0xFF38BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_racha_avatar),
                                contentDescription = "EVE Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column {
                            Text(text = "Racha AI", color = Color.White)
                            if (isThinking) {
                                Text(text = "Racha réfléchit...", color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A)
                )
            )
        },
        bottomBar = {
            Column {
                Surface(
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = { Text("اكتب أمرك أو اطلب برمجية/تصميماً...", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        IconButton(
                            onClick = {
                                if (messageText.isNotBlank() && !isThinking) {
                                    val userMsg = messageText
                                    chatMessages.add(ChatMessage(userMsg, true))
                                    messageText = ""
                                    isThinking = true

                                    scope.launch {
                                        delay(800)
                                        val reply = processRachaCommand(userMsg)
                                        isThinking = false
                                        chatMessages.add(ChatMessage(reply, false))
                                        JarvisVoiceEngine.speak(reply)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF38BDF8), CircleShape)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "إرسال", tint = Color.Black)
                        }
                    }
                }

                NavigationBar(
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color.White
                ) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenPrayer,
                        icon = { Icon(Icons.Default.CompassCalibration, contentDescription = "القبلة", tint = Color(0xFF38BDF8)) },
                        label = { Text("القبلة", color = Color.White) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenNotifications,
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "الإشعارات", tint = Color(0xFF38BDF8)) },
                        label = { Text("الإشعارات", color = Color.White) }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenSettings,
                        icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات", tint = Color(0xFF38BDF8)) },
                        label = { Text("الإعدادات", color = Color.White) }
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { msg ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Surface(
                        color = if (msg.isUser) Color(0xFF38BDF8) else Color(0xFF334155),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = msg.text,
                            color = if (msg.isUser) Color.Black else Color.White,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

fun processRachaCommand(input: String): String {
    val cleanInput = input.trim().lowercase()
    return when {
        cleanInput.contains("هاتف") || cleanInput.contains("وضع") -> {
            "تم فحص النظام بنجاح يا سيدي، وضبط إعدادات الهاتف حسب رغبتك."
        }
        cleanInput.contains("كود") || cleanInput.contains("تطبيق") || cleanInput.contains("لعبة") -> {
            "قمت بتحليل الطلب برمجياً عبر CodeEngine، وهيكل التطبيق جاهز للتنفيذ."
        }
        cleanInput.contains("صورة") || cleanInput.contains("فيديو") -> {
            "تمت معالجة العناصر البصرية عبر MediaGenerationEngine بدقة عالية."
        }
        else -> {
            "أنا أحلل طلبك بدقة يا سيدي: \"$input\". كيف ترغب في أن أمضي قدماً في تنفيذه؟"
        }
    }
}
