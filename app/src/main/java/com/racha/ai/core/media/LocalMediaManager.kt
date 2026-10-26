package com.racha.ai.core.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

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
                uri, projection, null, null, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "ملف صوتي"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    list.add(MediaItemData(id, title.trim(), ContentUris.withAppendedId(uri, id), "AUDIO", size, dur))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // فحص مباشر للمجلدات في حال كان MediaStore مقيداً
        if (list.isEmpty()) {
            listOf(File("/sdcard/Music"), File("/sdcard/Download")).forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in listOf("mp3", "m4a", "wav", "aac") }.take(30).forEach { file ->
                        list.add(MediaItemData(file.hashCode().toLong(), file.nameWithoutExtension, Uri.fromFile(file), "AUDIO", file.length()))
                    }
                }
            }
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
                uri, projection, null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = (if (titleCol != -1) cursor.getString(titleCol) else null)
                        ?: (if (nameCol != -1) cursor.getString(nameCol) else null) ?: "فيديو"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val dur = if (durCol != -1) cursor.getLong(durCol) else 0L
                    list.add(MediaItemData(id, title.trim(), ContentUris.withAppendedId(uri, id), "VIDEO", size, dur))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // فحص مباشر لمجلدات الفيديو في حال تقييد أندرويد
        if (list.isEmpty()) {
            listOf(File("/sdcard/Movies"), File("/sdcard/DCIM/Camera"), File("/sdcard/Download"), File("/sdcard/Videos")).forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "webm", "avi", "3gp") }.take(40).forEach { file ->
                        list.add(MediaItemData(file.hashCode().toLong(), file.nameWithoutExtension, Uri.fromFile(file), "VIDEO", file.length()))
                    }
                }
            }
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
                uri, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = if (nameCol != -1) cursor.getString(nameCol) else "صورة"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    list.add(MediaItemData(id, (name ?: "صورة").trim(), ContentUris.withAppendedId(uri, id), "PHOTO", size))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (list.isEmpty()) {
            listOf(File("/sdcard/DCIM/Camera"), File("/sdcard/Pictures"), File("/sdcard/Download")).forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().maxDepth(2).filter { it.isFile && it.extension.lowercase() in listOf("jpg", "jpeg", "png", "webp") }.take(40).forEach { file ->
                        list.add(MediaItemData(file.hashCode().toLong(), file.nameWithoutExtension, Uri.fromFile(file), "PHOTO", file.length()))
                    }
                }
            }
        }
        return list
    }
}
