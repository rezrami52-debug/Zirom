package com.racha.ai.ui.screens.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.racha.ai.ui.theme.RachaTheme

class ChatOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RachaTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(500.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    shadowElevation = 8.dp
                ) {
                    ChatScreen(
                        onOpenSettings = {},
                        onOpenNotifications = {},
                        onOpenPrayer = {},
                        onOpenRoutine = {}
                    )
                }
            }
        }
    }
}
