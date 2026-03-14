package com.example.placemate.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageUtils {
    private const val IMAGE_DIR_NAME = "images"

    fun createImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = File(context.cacheDir, IMAGE_DIR_NAME).apply { mkdirs() }
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    fun getContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun cropAndSave(context: Context, originalUri: Uri, rect: android.graphics.Rect): Uri? {
        return try {
            context.contentResolver.openInputStream(originalUri)?.use { inputStream ->
                val decoder = createDecoder(inputStream) ?: return null
                val width = decoder.width
                val height = decoder.height
                if (width == 0 || height == 0) {
                    decoder.recycle()
                    return null
                }

                val safeRect = android.graphics.Rect(
                    rect.left.coerceIn(0, width),
                    rect.top.coerceIn(0, height),
                    rect.right.coerceIn(0, width),
                    rect.bottom.coerceIn(0, height)
                )
                if (safeRect.width() <= 0 || safeRect.height() <= 0) {
                    decoder.recycle()
                    return null
                }

                val croppedBitmap = decoder.decodeRegion(safeRect, BitmapFactory.Options())
                decoder.recycle()
                if (croppedBitmap == null) {
                    return null
                }

                val croppedFile = createImageFile(context)
                FileOutputStream(croppedFile).use { output ->
                    croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                }
                croppedBitmap.recycle()
                getContentUri(context, croppedFile)
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Crop failed", e)
            null
        }
    }

    private fun createDecoder(inputStream: java.io.InputStream): BitmapRegionDecoder? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(inputStream)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(inputStream, false)
        }
    }
}
