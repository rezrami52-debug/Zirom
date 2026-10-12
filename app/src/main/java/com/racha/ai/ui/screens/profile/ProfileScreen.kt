package com.racha.ai.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.racha.ai.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val profile = state.profile

    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            delay(3000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ملفي الشخصي",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "هذه المعلومات تساعد Racha على فهمك. املأ ما تريد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ===== القسم 1: معلومات شخصية =====
            SectionHeader("👤 معلومات شخصية")
            Spacer(modifier = Modifier.height(8.dp))
            SimpleField(
                label = "الاسم",
                value = profile.name,
                onValueChange = { viewModel.updateProfile(profile.copy(name = it)) }
            )
            SimpleField(
                label = "المدينة",
                value = profile.city,
                onValueChange = { viewModel.updateProfile(profile.copy(city = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== القسم 2: العمل =====
            SectionHeader("💼 العمل")
            Spacer(modifier = Modifier.height(8.dp))
            SimpleField(
                label = "المهنة",
                value = profile.occupation,
                onValueChange = { viewModel.updateProfile(profile.copy(occupation = it)) }
            )
            SimpleField(
                label = "أيام العمل",
                value = profile.workDays,
                onValueChange = { viewModel.updateProfile(profile.copy(workDays = it)) },
                placeholder = "مثال: الإثنين-الجمعة"
            )
            TimeField(
                label = "بداية العمل",
                hour = profile.workStartHour,
                minute = profile.workStartMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(workStartHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(workStartMinute = it)) }
            )
            TimeField(
                label = "نهاية العمل",
                hour = profile.workEndHour,
                minute = profile.workEndMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(workEndHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(workEndMinute = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== القسم 3: النوم =====
            SectionHeader("🌙 النوم")
            Spacer(modifier = Modifier.height(8.dp))
            TimeField(
                label = "وقت الاستيقاظ",
                hour = profile.wakeUpHour,
                minute = profile.wakeUpMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(wakeUpHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(wakeUpMinute = it)) }
            )
            TimeField(
                label = "وقت النوم",
                hour = profile.sleepHour,
                minute = profile.sleepMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(sleepHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(sleepMinute = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== القسم 4: الوجبات =====
            SectionHeader("🍽️ الوجبات")
            Spacer(modifier = Modifier.height(8.dp))
            TimeField(
                label = "الفطور",
                hour = profile.breakfastHour,
                minute = profile.breakfastMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(breakfastHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(breakfastMinute = it)) }
            )
            TimeField(
                label = "الغداء",
                hour = profile.lunchHour,
                minute = profile.lunchMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(lunchHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(lunchMinute = it)) }
            )
            TimeField(
                label = "العشاء",
                hour = profile.dinnerHour,
                minute = profile.dinnerMinute,
                onHourChange = { viewModel.updateProfile(profile.copy(dinnerHour = it)) },
                onMinuteChange = { viewModel.updateProfile(profile.copy(dinnerMinute = it)) }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== القسم 5: الرياضة =====
            SectionHeader("🏃 الرياضة")
            Spacer(modifier = Modifier.height(8.dp))
            SimpleField(
                label = "نوع الرياضة",
                value = profile.sportType,
                onValueChange = { viewModel.updateProfile(profile.copy(sportType = it)) },
                placeholder = "مثال: جري، كرة قدم"
            )
            SimpleField(
                label = "أيام الرياضة",
                value = profile.sportDays,
                onValueChange = { viewModel.updateProfile(profile.copy(sportDays = it)) },
                placeholder = "مثال: الثلاثاء، الخميس"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ===== القسم 6: اهتمامات =====
            SectionHeader("❤️ اهتمامات")
            Spacer(modifier = Modifier.height(8.dp))
            SimpleField(
                label = "الهوايات",
                value = profile.hobbies,
                onValueChange = { viewModel.updateProfile(profile.copy(hobbies = it)) },
                placeholder = "مثال: قراءة، طبخ"
            )
            SimpleField(
                label = "ملاحظات إضافية",
                value = profile.notes,
                onValueChange = { viewModel.updateProfile(profile.copy(notes = it)) },
                placeholder = "أي شيء تريد أن يعرفه Racha",
                singleLine = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ الملف")
            }

            state.saveMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isSaved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun SimpleField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) {
            { Text(placeholder, style = MaterialTheme.typography.bodySmall) }
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = singleLine
    )
}

@Composable
fun TimeField(
    label: String,
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = hour.toString().padStart(2, '0'),
            onValueChange = { text ->
                val n = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                if (n in 0..23) onHourChange(n)
            },
            label = { Text("$label - ساعة") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = minute.toString().padStart(2, '0'),
            onValueChange = { text ->
                val n = text.filter { it.isDigit() }.take(2).toIntOrNull() ?: 0
                if (n in 0..59) onMinuteChange(n)
            },
            label = { Text("دقيقة") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
    }
}
