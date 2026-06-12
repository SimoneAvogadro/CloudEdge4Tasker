package online.avogadro.mearitaskerplugin.app

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dumps this app's logcat output (an app can read its own UID's log entries without
 * any permission) to a text file in the Downloads folder, so users can attach it to
 * bug reports. Includes TaskerPluginLibrary and Meari SDK log lines, since they run
 * in our process.
 */
object LogDumper {

    private const val TAG = "CE4T"

    /**
     * Returns a user-readable location of the saved file, or null on failure.
     * Performs I/O: call from a background thread.
     */
    @JvmStatic
    fun dumpToDownloads(context: Context): String? {
        val log = captureLogcat() ?: return null
        val fileName = "CloudEdge4Tasker-log-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(context, fileName, log)
            } else {
                saveLegacy(fileName, log)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to save log dump", t)
            null
        }
    }

    private fun captureLogcat(): String? = try {
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        text
    } catch (t: Throwable) {
        Log.e(TAG, "Failed to capture logcat", t)
        null
    }

    private fun saveViaMediaStore(context: Context, fileName: String, content: String): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: return null
        return Environment.DIRECTORY_DOWNLOADS + "/" + fileName
    }

    private fun saveLegacy(fileName: String, content: String): String? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) return null
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        return file.absolutePath
    }
}
