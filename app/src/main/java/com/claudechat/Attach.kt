package com.claudechat

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/** A file the user attached; [path] is where Claude sees it inside Ubuntu. */
data class Att(val name: String, val path: String, val uri: Uri)

object Attach {
    private fun nameOf(c: Context, u: Uri): String? =
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** The attachment folder as a /sdcard path (the folder picker may return /storage/emulated/0/...). */
    private fun dir(): String = Prefs.attachDir.value.trim().trimEnd('/').replaceFirst(Regex("^/storage/emulated/0"), "/sdcard")

    /** Copies a picked file into the attachment folder. With All files access the file is written at the exact path Claude reads. */
    fun save(c: Context, src: Uri): Att? = try {
        val name = nameOf(c, src) ?: "file"
        if (android.os.Environment.isExternalStorageManager()) {
            val folder = File(dir()).apply { mkdirs() }
            val dst = unique(folder, name)
            c.contentResolver.openInputStream(src)!!.use { i -> dst.outputStream().use { o -> i.copyTo(o) } }
            Att(dst.name, dst.path, Uri.fromFile(dst))
        } else {
            val cr = c.contentResolver
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, cr.getType(src) ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, dir().removePrefix("/sdcard/"))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val dst = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
            cr.openInputStream(src)!!.use { i -> cr.openOutputStream(dst)!!.use { o -> i.copyTo(o) } }
            cr.update(dst, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            val real = nameOf(c, dst) ?: name // MediaStore renames on collisions
            Att(real, dir() + "/" + real, dst)
        }
    } catch (e: Exception) { null }

    /** "name.ext" or, when taken, "name (1).ext", so an upload never overwrites an earlier one. */
    private fun unique(folder: File, name: String): File {
        val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
        val base = name.removeSuffix(ext)
        var f = File(folder, name); var n = 1
        while (f.exists()) { f = File(folder, "$base ($n)$ext"); n++ }
        return f
    }
}
