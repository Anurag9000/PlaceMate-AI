package com.example.placemate.core.utils

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object ImageUtils {
    fun createImageFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
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
            val inputStream = context.contentResolver.openInputStream(originalUri)
            val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // Ensure rect is within bitmap bounds
            val left = rect.left.coerceIn(0, originalBitmap.width)
            val top = rect.top.coerceIn(0, originalBitmap.height)
            val width = rect.width().coerceIn(0, originalBitmap.width - left)
            val height = rect.height().coerceIn(0, originalBitmap.height - top)

            if (width <= 0 || height <= 0) return null

            val croppedBitmap = android.graphics.Bitmap.createBitmap(originalBitmap, left, top, width, height)
            
            val croppedFile = createImageFile(context)
            val out = java.io.FileOutputStream(croppedFile)
            croppedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()

            Uri.fromFile(croppedFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
