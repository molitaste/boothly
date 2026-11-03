package com.boothly.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.boothly.app.data.Repo
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

object Images {
    private const val MAX_DIM = 1800

    fun newPhotoFile(): File =
        Repo.photoFile("IMG_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg")

    fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f)

    /** Copy a picture (gallery / scanner) into the app, resized & rotated. Returns the stored file name. */
    fun importUri(ctx: Context, uri: Uri): String? {
        val out = newPhotoFile()
        val ok = try {
            process({ ctx.contentResolver.openInputStream(uri) }, out)
        } catch (_: Exception) {
            false
        }
        if (!ok) out.delete()
        return if (ok) out.name else null
    }

    /** Shrink & rotate a camera photo in place. */
    fun normalize(f: File): Boolean {
        if (!f.exists() || f.length() == 0L) return false
        return try {
            process({ FileInputStream(f) }, f)
        } catch (_: Exception) {
            f.exists() && f.length() > 0
        }
    }

    private fun process(open: () -> InputStream?, out: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = open() ?: return false
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_DIM || bounds.outHeight / (sample * 2) >= MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false

        val rotation = try {
            open()?.use { ExifInterface(it).rotationDegrees } ?: 0
        } catch (_: Exception) {
            0
        }

        val m = Matrix()
        val scale = MAX_DIM.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) m.postScale(scale, scale)
        if (rotation != 0) m.postRotate(rotation.toFloat())
        val result = if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (result !== bmp) bmp.recycle()

        val tmp = File(out.parentFile, out.name + ".tmp")
        FileOutputStream(tmp).use { result.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        result.recycle()
        if (out.exists()) out.delete()
        return tmp.renameTo(out)
    }

    /** Save a bitmap as JPEG into the photos folder (long side max 2000 px). Returns the file name. */
    fun saveBitmap(src: Bitmap): String? {
        val scale = minOf(1f, 2000f / maxOf(src.width, src.height))
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = newPhotoFile()
        val tmp = File(out.parentFile, out.name + ".tmp")
        return try {
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (tmp.renameTo(out)) out.name else null
        } catch (_: Exception) {
            tmp.delete()
            null
        } finally {
            if (bmp !== src) bmp.recycle()
        }
    }

    /** Decode a small version of a picture (for PDF export). */
    fun decodeSmall(f: File, targetW: Int, targetH: Int): Bitmap? {
        if (!f.exists()) return null
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, b)
        if (b.outWidth <= 0) return null
        var sample = 1
        while (b.outWidth / (sample * 2) >= targetW && b.outHeight / (sample * 2) >= targetH) sample *= 2
        return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
