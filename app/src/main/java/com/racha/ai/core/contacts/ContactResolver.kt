package com.racha.ai.core.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

class ContactResolver(private val context: Context) {
    companion object { private const val TAG = "RachaContactResolver" }

    data class ContactInfo(val name: String, val phoneNumber: String)

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun searchContact(nameQuery: String): List<ContactInfo> {
        if (!hasPermission() || nameQuery.isBlank()) return emptyList()

        val contacts = mutableListOf<ContactInfo>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)

        try {
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                val normalizedQuery = normalizeArabic(nameQuery)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: continue
                    val number = it.getString(numberIndex) ?: continue
                    
                    val normalizedName = normalizeArabic(name)
                    val normalizedNumber = number.replace(Regex("\\s+"), "") // إزالة المسافات من الرقم للمطابقة

                    // 🌟 التحسين 3: مطابقة مرنة جداً (الاسم يحتوي الاستعلام، أو العكس، أو تطابق مع الرقم)
                    val isMatch = normalizedName.contains(normalizedQuery) || 
                                  normalizedQuery.contains(normalizedName) ||
                                  normalizedNumber.contains(normalizedQuery) ||
                                  normalizedQuery.contains(normalizedNumber)

                    if (isMatch) {
                        contacts.add(ContactInfo(name, number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "صلاحية مفقودة", e)
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في البحث", e)
        }
        return contacts
    }

    private fun normalizeArabic(text: String): String {
        return text.lowercase()
            .replace("إ", "ا").replace("أ", "ا").replace("آ", "ا")
            .replace("ة", "ه").replace("ى", "ي")
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "") // إزالة التشكيل
            .replace(Regex("\\s+"), "") // إزالة المسافات للمطابقة الأفضل
            .trim()
    }
}
