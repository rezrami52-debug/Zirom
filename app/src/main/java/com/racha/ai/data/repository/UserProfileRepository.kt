package com.racha.ai.data.repository

import android.content.Context
import com.racha.ai.data.local.RachaDatabase
import com.racha.ai.data.local.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository للملف الشخصي.
 */
class UserProfileRepository(context: Context) {

    private val dao = RachaDatabase.getDatabase(context).userProfileDao()

    fun getProfileFlow(): Flow<UserProfile?> = dao.getProfileFlow()

    suspend fun getProfile(): UserProfile = dao.getProfile() ?: UserProfile()

    suspend fun save(profile: UserProfile) {
        dao.save(profile.copy(lastUpdated = System.currentTimeMillis()))
    }

    suspend fun delete() {
        dao.delete()
    }

    /**
     * هل الملف مُعبَّأ؟
     */
    suspend fun isFilled(): Boolean {
        val profile = dao.getProfile() ?: return false
        return profile.isFilled()
    }

    /**
     * النص الجاهز للحقن في System Prompt.
     */
    suspend fun getPromptContext(): String {
        val profile = dao.getProfile() ?: return ""
        return if (profile.isFilled()) profile.toPromptContext() else ""
    }
}
