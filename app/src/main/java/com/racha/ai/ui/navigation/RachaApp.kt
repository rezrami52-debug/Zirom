package com.racha.ai.ui.navigation

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.racha.ai.core.faceauth.BiometricLockScreen
import com.racha.ai.core.faceauth.FaceAuthRepository
import com.racha.ai.core.faceauth.FaceCaptureScreen
import com.racha.ai.core.preferences.AppPreferences
import com.racha.ai.theme.AppThemeManager
import com.racha.ai.theme.RachaAppContainer
import com.racha.ai.ui.screens.CustomizationScreen
import com.racha.ai.ui.screens.SettingsScreen
import com.racha.ai.ui.screens.WelcomeScreen
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
    val appPrefs = remember { AppPreferences(context) }
    val faceRepo = remember { FaceAuthRepository(context) }
    val themeManager = remember { AppThemeManager(context) }

    val startScreen: Screen = when {
        enableBiometricLock && faceRepo.isFaceAuthEnabled() -> Screen.BiometricLock
        appPrefs.isOnboardingComplete() -> Screen.Chat
        else -> Screen.Onboarding
    }

    var currentScreen by remember { mutableStateOf<Screen>(startScreen) }

    // التحكم الذكي في الرجوع حسب الشاشة الحالية لمنع الإغلاق المفاجئ
    BackHandler(
        enabled = currentScreen !is Screen.Chat &&
                  currentScreen !is Screen.Onboarding &&
                  currentScreen !is Screen.BiometricLock
    ) {
        currentScreen = when (currentScreen) {
            is Screen.Customization -> Screen.Settings
            is Screen.Profile, is Screen.FaceEnrollment, is Screen.SecurityLog -> Screen.Settings
            else -> Screen.Chat
        }
    }

    RachaAppContainer(themeManager = themeManager) {
        when (currentScreen) {
            is Screen.BiometricLock -> BiometricLockScreen(
                onSuccess = { currentScreen = Screen.Chat },
                onCancel = { android.os.Process.killProcess(android.os.Process.myPid()) },
                onBypass = { currentScreen = Screen.Chat }
            )

            is Screen.Onboarding -> OnboardingScreen(
                onComplete = { currentScreen = Screen.Chat },
                onOpenApiKeySettings = { currentScreen = Screen.Settings }
            )

            is Screen.Chat -> ChatScreen(
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenNotifications = { currentScreen = Screen.Notifications },
                onOpenPrayer = { currentScreen = Screen.Prayer },
                onOpenRoutine = { currentScreen = Screen.Routine }
            )

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
                    faceRepo.saveFaceImage(bitmap)
                    faceRepo.setFaceAuthEnabled(true)
                    currentScreen = Screen.Settings
                },
                onCancel = { currentScreen = Screen.Settings }
            )

            is Screen.SecurityLog -> SecurityLogScreen(onBack = { currentScreen = Screen.Settings })
            is Screen.Notifications -> NotificationsScreen(onBack = { currentScreen = Screen.Chat })
            is Screen.Prayer -> PrayerScreen(onBack = { currentScreen = Screen.Chat })
            is Screen.Routine -> RoutineScreen(onBack = { currentScreen = Screen.Chat })

            is Screen.Customization -> CustomizationScreen(
                themeManager = themeManager,
                onBackClick = { currentScreen = Screen.Settings }
            )

            is Screen.Welcome -> WelcomeScreen(
                onOpenSettings = { currentScreen = Screen.Settings },
                onOpenChat = { currentScreen = Screen.Chat },
                onOpenNotifications = { currentScreen = Screen.Notifications },
                onOpenPrayer = { currentScreen = Screen.Prayer }
            )
        }
    }
}
