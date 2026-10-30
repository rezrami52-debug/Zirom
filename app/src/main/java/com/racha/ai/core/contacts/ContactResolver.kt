package com.racha.ai.core.contacts

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log

data class Contact(val name: String, val phoneNumber: String)

class ContactResolver(private val context: Context) {

    companion object {
        private const val TAG = "ContactResolver"
    }

    fun getAllContacts(): List<Contact> {
        val list = mutableListOf<Contact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameCol != -1) it.getString(nameCol) else ""
                    val num = if (numCol != -1) it.getString(numCol) else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        list.add(Contact(name.trim(), num.trim()))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contacts", e)
        }
        return list
    }

    fun searchContact(nameQuery: String): List<Contact> {
        val q = nameQuery.trim().lowercase()
        return getAllContacts().filter { it.name.lowercase().contains(q) }
    }

    /**
     * فلترة الأسماء التي تبدأ بحرف معين (مثال: حرف الميم أو 'م')
     */
    fun getContactsByNameStartingWith(letter: String): List<Contact> {
        val cleanLetter = letter.trim().take(1).lowercase()
        return getAllContacts().filter {
            it.name.trim().lowercase().startsWith(cleanLetter)
        }
    }

    /**
     * فلترة الأرقام التي تبدأ ببادئة معينة (مثال: الأرقام التي تبدأ بـ 23 أو 20 أو 98)
     */
    fun getContactsByNumberStartingWith(prefix: String): List<Contact> {
        val cleanPrefix = prefix.replace(Regex("[^0-9]"), "")
        if (cleanPrefix.isBlank()) return emptyList()

        return getAllContacts().filter {
            val rawNum = it.phoneNumber.replace(Regex("[^0-9]"), "")
            // يدعم الرقم المباشر (مثل 23xxxxxx) أو الرقم بالرمز الدولي التونسي (21623xxxxxx)
            rawNum.startsWith(cleanPrefix) || rawNum.startsWith("216$cleanPrefix")
        }
    }

    /**
     * جلب آخر رقم تم التفاعل معه في سجل المكالمات (الصادرة أو الواردة)
     */
    fun getLastCallNumber(): String? {
        return try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(CallLog.Calls.NUMBER)
                    if (idx != -1) it.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching last call log", e)
            null
        }
    }
}
