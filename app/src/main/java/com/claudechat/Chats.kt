package com.claudechat

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ChatsScreen(onBack: () -> Unit, onOpen: () -> Unit) {
    val chats by Engine.chats.collectAsState()
    val cur by Engine.currentId.collectAsState()
    val busy by Engine.busy.collectAsState()
    val list = chats.filter { it.title.isNotEmpty() }.sortedByDescending { it.updated }
    var del by remember { mutableStateOf<Chat?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.statusBarsPadding().fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                Text(stringResource(R.string.chats_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton({ Engine.newChat(); onOpen() }) { Icon(Icons.Filled.AddComment, stringResource(R.string.new_chat)) }
            }
        }
        if (list.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_chats), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().clickable { Engine.openChat(c.id); onOpen() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Mascot(c.id in busy, Modifier.size(28.dp, 20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (c.id == cur) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            DateUtils.getRelativeTimeSpanString(c.updated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton({ del = c }) { Icon(Icons.Filled.DeleteOutline, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    del?.let { c ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text(stringResource(R.string.delete_chat)) },
            text = { Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton({ Engine.deleteChat(c.id); del = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton({ del = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
