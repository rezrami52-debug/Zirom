package com.racha.ai.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItemData(
    val id: Long,
    val title: String,
    val uri: Uri,
    val type: String,
    val size: Long = 0L,
    val duration: Long = 0L
)

class LocalMediaManager(private val context: Context) {

    fun getLocalAudios(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DURATION
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol != -1) cursor.getString(titleCol) else null
                    val rawName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = (rawTitle ?: rawName ?: "ملف صوتي").trim()
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    list.add(MediaItemData(id, title, contentUri, "AUDIO", size, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getLocalVideos(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = if (titleCol != -1) cursor.getString(titleCol) else null
                    val rawName = if (nameCol != -1) cursor.getString(nameCol) else null
                    val title = (rawTitle ?: rawName ?: "فيديو").trim()
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val duration = if (durCol != -1) cursor.getLong(durCol) else 0L

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    list.add(MediaItemData(id, title, contentUri, "VIDEO", size, duration))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getLocalPhotos(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE
        )

        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "صورة"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    val contentUri = ContentUris.withAppendedId(uri, id)
                    list.add(MediaItemData(id, name.trim(), contentUri, "PHOTO", size))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
