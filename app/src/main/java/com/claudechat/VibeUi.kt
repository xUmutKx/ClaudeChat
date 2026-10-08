package com.claudechat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Settings > Vibration: pick what vibrates, and shape each pattern yourself (presets, tap your own rhythm, or type the milliseconds). */
@Composable
fun VibeSettings() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Text(tr("Your phone vibrates when something finishes, in the pattern you make. Works while Claude Chat runs in the background (keep-alive on).",
        "Bir şey bitince telefon senin yaptığın titreşimle haber verir. Claude Chat arka planda çalışırken (açık kalma açıkken) geçerli."), fontSize = 12.sp, color = cs.onSurfaceVariant)
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            var amp by remember { mutableFloatStateOf(Vib.amp().toFloat()) }
            Text(tr("Strength ${(amp * 100 / 255).toInt()}%", "Güç %${(amp * 100 / 255).toInt()}"), fontSize = 14.sp)
            Slider(amp, { amp = it }, valueRange = 20f..255f, onValueChangeFinished = { Vib.set("amp", amp.toInt().toString()) })
            Text(tr("Some phones only have one strength; then the slider has no effect.", "Bazı telefonlarda tek güç vardır; o zaman kaydırıcı etki etmez."), fontSize = 11.sp, color = cs.onSurfaceVariant)
        }
    }
    Vib.EVENTS.forEach { e -> EventCard(e) }
}

@Composable
private fun EventCard(e: Vib.Ev) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val on = Vib.state["${e.id}.on"] == "1"
    val pat = Vib.state["${e.id}.pat"] ?: e.def
    var edit by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr(e.en, e.tr), Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                Switch(on, { Vib.set("${e.id}.on", if (it) "1" else "0"); if (it) Vib.play(ctx, pat) })
            }
            PatternBar(pat)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton({ Vib.play(ctx, pat) }) { Text(tr("Test", "Dene")) }
                OutlinedButton({ edit = !edit }) { Text(if (edit) tr("Close", "Kapat") else tr("Shape it", "Şekil ver")) }
            }
            if (edit) {
                Text(tr("Presets", "Hazır"), fontSize = 12.sp, color = cs.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Vib.PRESETS.forEach { (n, p) -> AssistChip({ Vib.set("${e.id}.pat", p); Vib.play(ctx, p) }, { Text(tr(n.second, n.third)) }) }
                }
                Recorder { p -> Vib.set("${e.id}.pat", p); Vib.play(ctx, p) }
                var text by remember(pat) { mutableStateOf(pat) }
                OutlinedTextField(text, { text = it; if (Vib.parse(it).isNotEmpty()) Vib.set("${e.id}.pat", it) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tr("Milliseconds: pause, buzz, pause, buzz…", "Milisaniye: bekle, titre, bekle, titre…")) }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp))
                TextButton({ Vib.set("${e.id}.pat", e.def) }) { Text(tr("Back to default", "Varsayılana dön")) }
            }
        }
    }
}

/** The pattern drawn as bars: wide = long buzz, gaps = pauses. */
@Composable
private fun PatternBar(pat: String) {
    val t = Vib.parse(pat)
    val total = t.sum().coerceAtLeast(1L).toFloat()
    Row(Modifier.fillMaxWidth().height(22.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        t.forEachIndexed { i, ms ->
            if (ms > 0) Box(Modifier.weight(ms / total).fillMaxHeight().then(if (i % 2 == 1) Modifier.padding(vertical = 2.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)) else Modifier))
        }
    }
    Text("${t.sum()} ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Press and hold the pad to buzz, release to pause: the rhythm you tap becomes the pattern (saved after a short silence). */
@Composable
private fun Recorder(onDone: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var segs by remember { mutableStateOf(listOf<Long>()) }
    var lastUp by remember { mutableLongStateOf(0L) }
    var down by remember { mutableStateOf(false) }
    // a second of silence after the last release finishes the recording
    LaunchedEffect(segs, down) {
        if (segs.isNotEmpty() && !down) { delay(1100); if (segs.size > 1) onDone("0," + segs.joinToString(",")); segs = emptyList() }
    }
    Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp)).background(if (down) cs.primary else cs.primaryContainer)
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown()
                    val t0 = System.currentTimeMillis()
                    val gap = if (segs.isEmpty()) 0L else (t0 - lastUp).coerceIn(30L, 1500L)
                    down = true
                    waitForUpOrCancellation()
                    val t1 = System.currentTimeMillis()
                    down = false; lastUp = t1
                    if (segs.size < 30) segs = segs + (if (segs.isEmpty()) emptyList() else listOf(gap)) + (t1 - t0).coerceIn(30L, 1500L)
                }
            }
        }, contentAlignment = Alignment.Center) {
        Text(if (down) tr("Buzzing…", "Titriyor…") else if (segs.isEmpty()) tr("Your rhythm", "Ritmin") else tr("Recording… stop to save", "Kaydediyor… bırakınca kaydedilir"),
            color = if (down) cs.onPrimary else cs.onPrimaryContainer, fontSize = 13.sp)
    }
}
