package com.messerp.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

object ImageUtil {
    /** Reads a picked image, fixes rotation, shrinks it to at most [maxSide] pixels and returns JPEG bytes. */
    fun prepareJpeg(context: Context, uri: Uri, maxSide: Int = 480): ByteArray? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri) ?: return null
        // With inJustDecodeBounds the call returns null by design; only the size in "bounds" matters.
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide * 2 || bounds.outHeight / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        val matrix = Matrix()
        matrix.postScale(scale, scale)
        if (degrees != 0f) matrix.postRotate(degrees)
        val result = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        val out = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }
}
