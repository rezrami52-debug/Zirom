package com.racha.ai.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * يبحث في جهات الاتصال.
 */
class ContactResolver(private val context: Context) {

    companion object {
        private const val TAG = "RachaContactResolver"
    }

    data class ContactInfo(
        val name: String,
        val phoneNumber: String
    )

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * البحث عن جهة اتصال بالاسم.
     */
    fun searchContact(nameQuery: String): List<ContactInfo> {
        if (!hasPermission()) {
            Log.w(TAG, "صلاحية READ_CONTACTS غير ممنوحة")
            return emptyList()
        }

        if (nameQuery.isBlank()) return emptyList()

        val contacts = mutableListOf<ContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numberIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                val normalizedQuery = normalizeArabic(nameQuery)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: continue
                    val number = it.getString(numberIndex) ?: continue

                    if (normalizeArabic(name).contains(normalizedQuery)) {
                        contacts.add(ContactInfo(name, number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "صلاحية مفقودة", e)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ", e)
        }

        Log.d(TAG, "وجدت ${contacts.size} لـ '$nameQuery'")
        return contacts
    }

    private fun normalizeArabic(text: String): String {
        return text
            .lowercase()
            .replace("إ", "ا")
            .replace("أ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("\\s+"), "")
            .trim()
    }
}
