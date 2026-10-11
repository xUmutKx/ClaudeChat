package com.claudechat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tool output of the current turn as one line above the composer; tap to expand. Collapses when the turn ends. */
@Composable
fun ToolStrip(tools: List<Msg>, working: Boolean) {
    if (tools.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(working) { if (!working) open = false }
    LaunchedEffect(tools.size) { if (open) scroll.animateScrollTo(scroll.maxValue) }
    Surface(Modifier.fillMaxWidth().padding(horizontal = 10.dp), shape = RoundedCornerShape(14.dp), color = cs.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Terminal, null, Modifier.size(13.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(tools.last().text, Modifier.weight(1f), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
            }
            if (open) Column(Modifier.heightIn(max = 220.dp).verticalScroll(scroll).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tools.forEach { ToolItem(it) }
            }
        }
    }
}
