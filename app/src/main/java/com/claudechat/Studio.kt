package com.claudechat

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.provider.MediaStore
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File

/** Studio: an SVG (or pixel art made of rects) the AI wrote, drawn right in the chat, with Save and Share. Scripts and network are off. */
private fun clean(svg: String) = svg.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "").replace(Regex("\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*')", RegexOption.IGNORE_CASE), "")

private fun ratioOf(svg: String): Float {
    val vb = Regex("viewBox\\s*=\\s*\"([^\"]*)\"").find(svg)?.groupValues?.get(1)?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }
    if (vb != null && vb.size == 4 && vb[2] > 0 && vb[3] > 0) return (vb[2] / vb[3]).coerceIn(0.25f, 4f)
    return 1f
}

private fun html(svg: String) = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>html,body{margin:0;height:100%;background:transparent}" +
    "body{display:flex;align-items:center;justify-content:center}svg{width:100%;height:100%;image-rendering:pixelated}</style></head><body>" + clean(svg) + "</body></html>"

private fun newWeb(c: Context) = WebView(c).apply {
    settings.javaScriptEnabled = false; settings.blockNetworkLoads = true; settings.allowFileAccess = false
    setBackgroundColor(0); isVerticalScrollBarEnabled = false
}

/** Draws the SVG big (up to 1024 px) in an off-screen WebView and hands back the bitmap (null if it could not). */
private fun renderPng(c: Context, svg: String, ratio: Float, done: (Bitmap?) -> Unit) {
    val w = 1024; val h = (w / ratio).toInt().coerceIn(256, 2048)
    val wv = newWeb(c)
    wv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    wv.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
    wv.layout(0, 0, w, h)
    wv.webViewClient = object : WebViewClient() {
        override fun onPageFinished(v: WebView, url: String?) {
            v.postDelayed({
                val bm = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) } } catch (e: Throwable) { null }
                v.destroy(); done(bm)
            }, 400)
        }
    }
    wv.loadDataWithBaseURL(null, html(svg), "text/html", "utf-8", null)
}

private fun saveToPictures(c: Context, bm: Bitmap): Boolean = try {
    val name = "studio_" + System.currentTimeMillis() + ".png"
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ClaudeChat")
    }
    val uri = c.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)!!
    c.contentResolver.openOutputStream(uri)!!.use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
    true
} catch (e: Exception) { false }

@Composable
fun SvgCard(svg: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val ratio = remember(svg) { ratioOf(svg) }
    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceContainerHigh) {
        Column(Modifier.padding(8.dp)) {
            AndroidView(
                factory = { c -> newWeb(c) },
                update = { v -> if (v.tag != svg) { v.tag = svg; v.loadDataWithBaseURL(null, html(svg), "text/html", "utf-8", null) } },
                modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(8.dp)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({
                    renderPng(ctx, svg, ratio) { bm ->
                        val ok = bm != null && saveToPictures(ctx, bm)
                        Toast.makeText(ctx, if (ok) tr("Saved to Pictures/ClaudeChat", "Resimler/ClaudeChat'e kaydedildi") else tr("Could not save", "Kaydedilemedi"), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Save PNG", "PNG kaydet")) }
                TextButton({
                    renderPng(ctx, svg, ratio) { bm ->
                        if (bm == null) { Toast.makeText(ctx, tr("Could not share", "Paylaşılamadı"), Toast.LENGTH_SHORT).show(); return@renderPng }
                        val f = File(ctx.cacheDir, "studio").apply { mkdirs() }.let { File(it, "studio_" + System.currentTimeMillis() + ".png") }
                        f.outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(tr("Share", "Paylaş")) }
            }
        }
    }
}
