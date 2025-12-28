package com.kontext.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val imagesDir: File
        get() = File(context.filesDir, "story_images").apply {
            if (!exists()) mkdirs()
        }

    suspend fun saveBitmapToInternalStorage(bitmap: Bitmap, filename: String = "img_${System.currentTimeMillis()}.png"): String = withContext(Dispatchers.IO) {
        val file = File(imagesDir, filename)
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return@withContext file.absolutePath
    }

    suspend fun loadBitmapFromPath(path: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (file.exists()) {
            BitmapFactory.decodeFile(file.absolutePath)
        } else {
            null
        }
    }
}
