package eu.kanade.tachiyomi.data.connections.discord

import android.content.Context
import android.net.Uri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Uploads covers that only exist on this device (custom covers, local source covers) to a public
 * file host, since Discord can only display images it can fetch from an http(s) URL. Tries uguu.se
 * first and falls back to catbox.moe. Results are cached per file so the same cover isn't uploaded
 * again on every chapter change.
 */
internal object DiscordImageUploader {
    private const val TAG = "DiscordImageUploader"
    private const val UGUU_URL = "https://uguu.se/upload.php?output=text"
    private const val CATBOX_URL = "https://catbox.moe/user/api.php"
    private const val MAX_COVER_BYTES = 10 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000

    private val uploadCache = ConcurrentHashMap<String, String>()

    suspend fun resolveUrl(file: File): String? = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:${file.lastModified()}"
        uploadCache[key]?.let { return@withContext it }

        val bytes = readCover { file.readBytes() } ?: return@withContext null
        upload(bytes)?.also { uploadCache[key] = it }
    }

    /** [source] is a `content://` or `file://` URI, or an absolute file path. */
    suspend fun resolveUrl(context: Context, source: String): String? = withContext(Dispatchers.IO) {
        uploadCache[source]?.let { return@withContext it }

        val bytes = readCover {
            if (source.startsWith("content://") || source.startsWith("file://")) {
                context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
            } else {
                File(source).readBytes()
            }
        } ?: return@withContext null
        upload(bytes)?.also { uploadCache[source] = it }
    }

    private inline fun readCover(read: () -> ByteArray?): ByteArray? = try {
        read()?.takeIf { it.isNotEmpty() && it.size <= MAX_COVER_BYTES }
    } catch (e: Exception) {
        Logger.w(TAG) { "Failed to read cover: ${e.message}" }
        null
    }

    private fun upload(bytes: ByteArray): String? =
        uploadTo(UGUU_URL, emptyMap(), "files[]", bytes)
            ?: uploadTo(CATBOX_URL, mapOf("reqtype" to "fileupload"), "fileToUpload", bytes)

    private fun uploadTo(url: String, fields: Map<String, String>, fileField: String, bytes: ByteArray): String? {
        val extension = extensionFor(bytes)
        val boundary = "rokku-${System.nanoTime()}"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS * 2
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                conn.outputStream.use { out ->
                    fields.forEach { (name, value) ->
                        out.writeText("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                    }
                    out.writeText(
                        "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"$fileField\"; filename=\"cover.$extension\"\r\n" +
                            "Content-Type: image/$extension\r\n\r\n",
                    )
                    out.write(bytes)
                    out.writeText("\r\n--$boundary--\r\n")
                }

                if (conn.responseCode !in 200..299) {
                    Logger.w(TAG) { "Upload failed: HTTP ${conn.responseCode}" }
                    null
                } else {
                    conn.inputStream.bufferedReader().use { it.readText() }
                        .trim()
                        .takeIf { it.startsWith("http://") || it.startsWith("https://") }
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Upload failed: ${e.message}" }
            null
        }
    }

    private fun OutputStream.writeText(text: String) = write(text.toByteArray())

    private fun extensionFor(bytes: ByteArray): String = when {
        bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.ISO_8859_1) == "RIFF" &&
            String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "webp"

        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"

        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpeg"

        else -> "png"
    }
}
