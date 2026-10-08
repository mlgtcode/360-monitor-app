package app.monitor360.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExportIo {
    private const val DIR = "exports"
    private const val KEEP_FILES = 20

    fun share(context: Context, file: ExportFile, subject: String = "360 Monitor export") {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.sortedBy { it.lastModified() }?.dropLast(KEEP_FILES - 1)?.forEach { it.delete() }
        val target = File(dir, file.name).apply { writeBytes(file.bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", target)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun save(context: Context, uri: Uri, bytes: ByteArray) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Could not open the chosen location")
        stream.use { it.write(bytes) }
    }

    fun sizeLabel(bytes: Int): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    }
}
