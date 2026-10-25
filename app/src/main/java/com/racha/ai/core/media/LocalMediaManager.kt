package com.racha.ai.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

data class MediaItemData(
    val id: Long,
    val title: String,
    val uri: Uri,
    val type: String
)

class LocalMediaManager(private val context: Context) {

    private fun getAudioUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getVideoUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun getImageUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
    }

    fun getLocalAudios(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val audioUri = getAudioUri()
        try {
            val cursor = context.contentResolver.query(
                audioUri,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val nameCol = it.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = if (titleCol != -1) it.getString(titleCol) else null
                    val rawName = if (nameCol != -1) it.getString(nameCol) else null
                    val title = (rawTitle ?: rawName ?: "ملف صوتي").trim()
                    val uri = ContentUris.withAppendedId(audioUri, id)
                    list.add(MediaItemData(id, title, uri, "AUDIO"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getLocalVideos(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME
        )
        val videoUri = getVideoUri()
        try {
            val cursor = context.contentResolver.query(
                videoUri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val rawTitle = if (titleCol != -1) it.getString(titleCol) else null
                    val rawName = if (nameCol != -1) it.getString(nameCol) else null
                    val title = (rawTitle ?: rawName ?: "فيديو").trim()
                    val uri = ContentUris.withAppendedId(videoUri, id)
                    list.add(MediaItemData(id, title, uri, "VIDEO"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getLocalPhotos(): List<MediaItemData> {
        val list = mutableListOf<MediaItemData>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val imageUri = getImageUri()
        try {
            val cursor = context.contentResolver.query(
                imageUri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val name = (if (nameCol != -1) it.getString(nameCol) else null) ?: "صورة"
                    val uri = ContentUris.withAppendedId(imageUri, id)
                    list.add(MediaItemData(id, name.trim(), uri, "PHOTO"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
