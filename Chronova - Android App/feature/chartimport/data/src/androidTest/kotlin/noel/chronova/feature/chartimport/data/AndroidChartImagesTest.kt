package noel.chronova.feature.chartimport.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import noel.chronova.feature.chartimport.domain.ImportApiException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real decoding, turning and scaling on a device; files in this test app's own cache. */
@RunWith(AndroidJUnit4::class)
class AndroidChartImagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val images = AndroidChartImages(context)
    private val made = mutableListOf<File>()

    @After
    fun tearDown() {
        made.forEach { it.delete() }
        runBlocking { images.clear() }
    }

    /** A JPEG [width]×[height], left half red, with an EXIF orientation and a location. */
    private fun photo(width: Int, height: Int, orientation: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (x in 0 until width / 2) for (y in 0 until height step 50) setPixel(x, y, Color.RED)
        }
        val file = File(context.cacheDir, "test-${System.nanoTime()}.jpg").also { made += it }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "14/1,35/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun size(jpeg: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        return options.outWidth to options.outHeight
    }

    @Test
    fun aLargePhotoIsTurnedUprightScaledAndStrippedOfItsLocation() = runBlocking {
        val jpeg = images.prepare(photo(4000, 1000, ExifInterface.ORIENTATION_ROTATE_90).toString())

        assertEquals(512 to 2048, size(jpeg)) // turned a quarter, long side 2048
        val exif = ExifInterface(ByteArrayInputStream(jpeg))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED))
    }

    @Test
    fun aVeryLargePhotoIsDecodedSmallerFirst() = runBlocking {
        assertEquals(2048 to 1024, size(images.prepare(photo(4800, 2400, ExifInterface.ORIENTATION_NORMAL).toString()))) // decoded at half, then scaled
    }

    @Test
    fun aSmallImageKeepsItsSize() = runBlocking {
        assertEquals(800 to 600, size(images.prepare(photo(800, 600, ExifInterface.ORIENTATION_NORMAL).toString())))
    }

    @Test
    fun somethingUnreadableIsABadImage() = runBlocking {
        val text = File(context.cacheDir, "not-an-image.jpg").apply { writeText("hello") }.also { made += it }
        for (uri in listOf(Uri.fromFile(text).toString(), "content://nowhere/at/all")) {
            val error = runCatching { images.prepare(uri) }.exceptionOrNull()
            assertEquals(uri, ImportApiException.Kind.BAD_IMAGE, (error as ImportApiException).kind)
        }
    }

    @Test
    fun photosForTheCameraGoInTheImportFolderAndAreCleared() = runBlocking {
        val uri = Uri.parse(images.newPhotoUri())
        assertEquals("${context.packageName}.imports", uri.authority)
        context.contentResolver.openOutputStream(uri)!!.use { it.write(byteArrayOf(1, 2, 3)) }
        val folder = File(context.cacheDir, "import")
        assertEquals(1, folder.listFiles()!!.size)

        images.clear()
        assertTrue(folder.listFiles()!!.isEmpty())
    }
}
