package com.racha.ai.ui.navigation                      
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*                       
import androidx.compose.ui.platform.LocalContext
import com.racha.ai.core.faceauth.BiometricLockScreen
import com.racha.ai.core.faceauth.FaceCaptureScreen
import com.racha.ai.data.repository.ChatMemoryRepository
import com.racha.ai.theme.AppThemeManager
import com.racha.ai.theme.RachaAppContainer
import com.racha.ai.ui.screens.CustomizationScreen
import com.racha.ai.ui.screens.SettingsScreen           
import com.racha.ai.ui.screens.WelcomeScreen
import com.racha.ai.ui.screens.chat.ChatHistoryScreen   
import com.racha.ai.ui.screens.chat.ChatScreen
import com.racha.ai.ui.screens.notifications.NotificationsScreen
import com.racha.ai.ui.screens.onboarding.OnboardingScreen
import com.racha.ai.ui.screens.prayer.PrayerScreen      
import com.racha.ai.ui.screens.profile.ProfileScreen
import com.racha.ai.ui.screens.routine.RoutineScreen    
import com.racha.ai.ui.screens.security.SecurityLogScreen

sealed class Screen {
    object Onboarding : Screen()
    object Chat : Screen()
    object ChatHistory : Screen()
    object Settings : Screen()
    object Notifications : Screen()
    object Prayer : Screen()
    object Routine : Screen()
    object Profile : Screen()
    object FaceEnrollment : Screen()
    object BiometricLock : Screen()
    object SecurityLog : Screen()
    object Welcome : Screen()
    object Customization : Screen()
}

@Composable
fun RachaApp(
    enableBiometricLock: Boolean = true
) {
    val context = LocalContext.current
    val themeManager = remember { AppThemeManager(context) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }

    // معالجة زر الرجوع الفيزيائي للهاتف
    BackHandler(enabled = currentScreen != Screen.Chat) {
        currentScreen = when (currentScreen) {
            is Screen.Customization,
            is Screen.Profile,
            is Screen.FaceEnrollment,
            is Screen.SecurityLog -> Screen.Settings
            else -> Screen.Chat
        }
    }

    RachaAppContainer(themeManager = themeManager) {
        when (currentScreen) {
            is Screen.Welcome -> WelcomeScreen(
                onOpenChat = { currentScreen = Screen.Chat },
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenNotifications = { currentScreen = Screen.Notifications },
                onOpenPrayer = { currentScreen = Screen.Prayer }
            )

            is Screen.Onboarding -> OnboardingScreen(
                onComplete = { currentScreen = Screen.Chat },
                onOpenApiKeySettings = { currentScreen = Screen.Settings }
            )

            is Screen.Chat -> ChatScreen(
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenNotifications = { currentScreen = Screen.Notifications },
                onOpenPrayer = { currentScreen = Screen.Prayer },
                onOpenRoutine = { currentScreen = Screen.Routine },
                onOpenHistory = { currentScreen = Screen.ChatHistory }
            )

            is Screen.ChatHistory -> {
                val chatRepo = remember { ChatMemoryRepository(context) }
                val conversations by chatRepo.getAllConversations().collectAsState(initial = emptyList())
                ChatHistoryScreen(
                    conversations = conversations,
                    currentConversationId = "",
                    onConversationSelected = {
                        currentScreen = Screen.Chat
                    },
                    onNewChat = {
                        currentScreen = Screen.Chat
                    },
                    onBack = { currentScreen = Screen.Chat }
                )
            }

            is Screen.Settings -> SettingsScreen(
                themeManager = themeManager,
                onOpenCustomization = { currentScreen = Screen.Customization },
                onBack = { currentScreen = Screen.Chat },
                onOpenProfile = { currentScreen = Screen.Profile },
                onOpenFaceEnrollment = { currentScreen = Screen.FaceEnrollment },
                onOpenSecurityLog = { currentScreen = Screen.SecurityLog }
            )

            is Screen.Profile -> ProfileScreen(onBack = { currentScreen = Screen.Settings })

            is Screen.FaceEnrollment -> FaceCaptureScreen(
                onCaptured = { bitmap: Bitmap ->
                    currentScreen = Screen.Settings
                },
                onCancel = { currentScreen = Screen.Settings }
            )

            is Screen.BiometricLock -> BiometricLockScreen(
                onSuccess = { currentScreen = Screen.Chat },
                onCancel = { currentScreen = Screen.Chat },
                onBypass = { currentScreen = Screen.Chat }
            )

            is Screen.SecurityLog -> SecurityLogScreen(onBack = { currentScreen = Screen.Settings })
            is Screen.Customization -> CustomizationScreen(
                themeManager = themeManager,
                onBackClick = { currentScreen = Screen.Settings }
            )
            is Screen.Notifications -> NotificationsScreen(onBack = { currentScreen = Screen.Chat })
            is Screen.Prayer -> PrayerScreen(onBack = { currentScreen = Screen.Chat })
            is Screen.Routine -> RoutineScreen(onBack = { currentScreen = Screen.Chat })
        }
    }
}
