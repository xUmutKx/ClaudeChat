package com.claudechat

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns

/** A file the user attached; [path] is where Claude sees it inside Ubuntu. */
data class Att(val name: String, val path: String, val uri: Uri)

object Attach {
    private fun nameOf(c: Context, u: Uri): String? =
        c.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }

    /** Copies a picked file into Download/projects (no storage permission needed for files we create). */
    fun save(c: Context, src: Uri): Att? = try {
        val cr = c.contentResolver
        val name = nameOf(c, src) ?: "file"
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, cr.getType(src) ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/projects")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val dst = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
        cr.openInputStream(src)!!.use { i -> cr.openOutputStream(dst)!!.use { o -> i.copyTo(o) } }
        cr.update(dst, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        val real = nameOf(c, dst) ?: name // MediaStore renames on collisions
        Att(real, Prefs.attachDir.value.trimEnd('/') + "/" + real, dst)
    } catch (e: Exception) { null }
}
