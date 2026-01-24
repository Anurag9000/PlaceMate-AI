import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.BitmapFactory
import java.io.FileOutputStream
import java.io.InputStream

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
        var inputStream: java.io.InputStream? = null
        return try {
            inputStream = context.contentResolver.openInputStream(originalUri) ?: return null
            
            // Use BitmapRegionDecoder to crop without loading the full image
            val decoder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.graphics.BitmapRegionDecoder.newInstance(inputStream)
            } else {
                @Suppress("DEPRECATION")
                android.graphics.BitmapRegionDecoder.newInstance(inputStream, false)
            }

            val width = decoder?.width ?: 0
            val height = decoder?.height ?: 0
            
            if (decoder == null || width == 0 || height == 0) return null

            // Ensure rect is valid
            val safeRect = android.graphics.Rect(
                rect.left.coerceIn(0, width),
                rect.top.coerceIn(0, height),
                rect.right.coerceIn(0, width),
                rect.bottom.coerceIn(0, height)
            )

            if (safeRect.width() <= 0 || safeRect.height() <= 0) return null

            val options = BitmapFactory.Options()
            val croppedBitmap = decoder.decodeRegion(safeRect, options)
            decoder.recycle()

            val croppedFile = createImageFile(context)
            val out = FileOutputStream(croppedFile)
            croppedBitmap?.let {
                it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                it.recycle() // Recycle after compress
            }
            out.flush()
            out.close()

            Uri.fromFile(croppedFile)
        } catch (e: Exception) {
            android.util.Log.e("ImageUtils", "Crop failed", e)
            null
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }
}
