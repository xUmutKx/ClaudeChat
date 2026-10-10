package com.claudechat

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val FILE_RE = Regex("(?:/sdcard|/storage/emulated/0)/[^\\s`'\"()<>\\[\\]|]+?\\.(?:apk|zip|pdf|png|jpe?g|gif|mp4|txt|md|json|csv|log|gz|7z)(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)

/** A file Claude names in its answer (a built APK, a zip, a PDF ...) as a tappable chip: an APK opens the installer, anything else opens in its viewer. */
@Composable
fun FileChips(text: String) {
    val ctx = LocalContext.current
    val paths = remember(text) { FILE_RE.findAll(text).map { it.value }.distinct().take(6).toList() }
    if (paths.isEmpty()) return
    val files by produceState(emptyList<File>(), paths) {
        value = withContext(Dispatchers.IO) { paths.map { File(it) }.filter { it.isFile && it.length() > 0 } }
    }
    files.forEach { f ->
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(top = 6.dp).clickable { openFile(ctx, f) }) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (f.extension.equals("apk", true)) Icons.Filled.Android else Icons.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(f.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sizeLabel(f.length()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun sizeLabel(b: Long) = if (b >= 1_000_000) "%.1f MB".format(b / 1e6) else "%d KB".format((b / 1000).coerceAtLeast(1))

private fun openFile(ctx: Context, f: File) {
    try {
        if (f.extension.equals("apk", true)) { TermuxInstall.install(ctx, f); return }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) { }
}
