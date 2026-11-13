package noel.chronova.feature.chartimport.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import noel.chronova.feature.chartimport.domain.ChartImages
import noel.chronova.feature.chartimport.domain.ImageToSend
import noel.chronova.feature.chartimport.domain.ImportApiException
import kotlin.math.max

/**
 * Photos for imports live in cache/import, shared with the camera app through
 * [ChartImportFileProvider]; picked images are read through their content URIs. Before sending,
 * an image is turned upright (its EXIF orientation), scaled to [ImageToSend.MAX_SIDE_PX] and
 * re-encoded as JPEG: the result carries no metadata, so no location leaves the phone.
 */
class AndroidChartImages(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ChartImages {
    private val context = context.applicationContext
    private val folder get() = File(context.cacheDir, FOLDER)

    override suspend fun newPhotoUri(): String = withContext(io) {
        folder.mkdirs()
        val file = File(folder, "photo-${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.imports", file).toString()
    }

    override suspend fun prepare(uri: String): ByteArray = withContext(io) {
        try {
            prepareOrNull(uri)
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null // a picked image whose access has gone
        } ?: throw ImportApiException(ImportApiException.Kind.BAD_IMAGE)
    }

    override suspend fun clear() {
        withContext(io) { folder.listFiles()?.forEach { it.delete() } }
    }

    private fun prepareOrNull(uri: String): ByteArray? {
        val resolver = context.contentResolver
        val source = uri.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds only: decodeStream answers null here by design, so only a missing stream means failure.
        val stream = resolver.openInputStream(source) ?: return null
        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Decode at the largest power-of-two step down that still keeps the long side at least MAX_SIDE_PX.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ImageToSend.MAX_SIDE_PX) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val orientation = resolver.openInputStream(source)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        // A smaller image keeps its size; a larger one comes down to MAX_SIDE_PX.
        val scale = minOf(1f, ImageToSend.MAX_SIDE_PX.toFloat() / max(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(270f); postScale(-1f, 1f) }
            }
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        return ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, ImageToSend.JPEG_QUALITY, out)
            upright.recycle()
            out.toByteArray()
        }
    }

    private companion object {
        const val FOLDER = "import"
    }
}
