package com.example.controlherbal.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Utilidades de imágenes para la cámara: decodificación acotada y limpieza de temporales. */
object ImageUtils {

    /**
     * Decodifica reduciendo el tamaño para limitar memoria y datos enviados. Al trabajar con un
     * Bitmap (no con el JPEG original) los metadatos EXIF (p. ej. GPS) no se transmiten.
     */
    fun decodeScaled(path: String, maxDimension: Int = AppConstants.MAX_IMAGE_DIMENSION_PX): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension * 2 || bounds.outHeight / sample > maxDimension * 2) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null

        val largest = maxOf(decoded.width, decoded.height)
        if (largest <= maxDimension) return decoded
        val ratio = maxDimension.toFloat() / largest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /** Borra un archivo temporal sin lanzar excepciones. */
    fun deleteQuietly(path: String?) {
        if (path == null) return
        try { File(path).delete() } catch (_: Exception) { /* nada que hacer */ }
    }

    /** Elimina fotos temporales antiguas del directorio de cámara. */
    fun purgeCameraCache(dir: File, maxAgeMs: Long = 60 * 60 * 1000L) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) f.delete()
        }
    }
}
