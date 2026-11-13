package noel.chronova.feature.export.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import noel.chronova.feature.export.domain.ExportFiles
import noel.chronova.feature.export.domain.ExportedFile

/**
 * Exports live in `cache/exports`, cleared before each one, so at most one export is kept
 * (and Android may clear the cache at any time). They are shared through this module's
 * FileProvider, so the receiving app gets a read-only content URI and no file path.
 */
class CacheExportFiles(private val context: Context) : ExportFiles {
    override suspend fun save(fileName: String, mimeType: String, bytes: ByteArray): ExportedFile = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR)
        dir.deleteRecursively()
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        ExportedFile(fileName, mimeType, uri.toString())
    }

    private companion object {
        const val DIR = "exports"
    }
}
