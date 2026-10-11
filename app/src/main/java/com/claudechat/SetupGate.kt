package com.claudechat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * First-run setup gate. The chat is not drawn until the required steps are real: Termux installed with RUN_COMMAND granted,
 * the bridge answering on 127.0.0.1, and Claude signed in. Nothing is stored; every check reads the live state, so a lost
 * bridge or a sign-out brings the wizard back. Once the wizard was entered it stays until "Done", so the optional steps show.
 */
@Composable
fun SetupGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val bridge by Engine.bridge.collectAsState()
    var installed by remember { mutableStateOf(Termux.installed(ctx)) }
    var granted by remember { mutableStateOf(Termux.granted(ctx)) }
    var auth by remember { mutableStateOf<Engine.Auth?>(null) }
    var ready by remember { mutableStateOf(false) }
    // false until the first real answer: no wizard flash for someone who is already set up
    var checked by remember { mutableStateOf(false) }
    var walking by rememberSaveable { mutableStateOf(false) }
    val termux = installed && granted

    LaunchedEffect(Unit) {
        if (termux) Engine.checkBridge()   // one look at launch; the bridge state starts as "unknown"
        while (true) {
            delay(2000)
            installed = Termux.installed(ctx); granted = Termux.granted(ctx)
            // only a bridge confirmed down is started again: checkBridge() resets the state while it looks, which would flicker the chat
            if (installed && granted && bridge == -1) Engine.checkBridge()
        }
    }
    LaunchedEffect(bridge) { while (bridge == 200) { Engine.authStatus()?.let { auth = it }; delay(6000) } }
    LaunchedEffect(termux, bridge, auth) {
        if (!termux) ready = false
        // 0 (starting) and -2 (checking) are transient: keep the last answer instead of tearing the chat down
        else if (bridge != -2 && bridge != 0) ready = bridge == 200 && auth?.claudeIn == true
        if (!checked && (!termux || bridge == -1 || (bridge == 200 && auth != null))) checked = true
        if (checked && !ready) walking = true
    }

    when {
        !checked -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        walking || !ready -> SetupWizard(installed, granted, bridge, auth, ready, { auth = it ?: auth }, { walking = false })
        else -> content()
    }
}

@Composable
private fun SetupWizard(installed: Boolean, granted: Boolean, bridge: Int, auth: Engine.Auth?, ready: Boolean, onAuth: (Engine.Auth?) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ok1 = installed && granted && bridge == 200
    val ok2 = ok1 && auth?.claudeIn == true
    var idx by rememberSaveable { mutableStateOf(0) }
    // a required step that turns green moves on by itself; going back to it does not re-trigger this
    LaunchedEffect(ok1, ok2) {
        if (ok1 && idx == 0) idx = 1
        if (ok2 && idx == 1) idx = 2
    }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Set up Claude on this phone", "Claude'u bu telefona kur"), fontFamily = FontFamily.Serif, fontSize = 22.sp)
            LinearProgressIndicator(progress = { (idx + 1) / 4f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Text(tr("Step ${idx + 1} of 4", "Adım ${idx + 1} / 4"), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (idx) {
                0 -> TermuxStep(installed, granted, bridge, ok1) { idx = 1 }
                1 -> ClaudeStep(ok1, ok2, auth, onAuth) { idx = 2 }
                2 -> GitHubStep(ok1, auth, onAuth, { idx = 1 }) { idx = 3 }
                else -> SummaryStep(ok1, ok2, auth?.ghIn == true, ready, onDone)
            }
        }
    }
}

@Composable
private fun TermuxStep(installed: Boolean, granted: Boolean, bridge: Int, ok1: Boolean, next: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val dl by TermuxInstall.state.collectAsState()
    val note by Termux.note.collectAsState()
    val err by Engine.bridgeError.collectAsState()
    // the answer does not matter here: the state is polled and the step turns green by itself
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Step(1, tr("Termux and the connection", "Termux ve bağlantı"), ok1,
        tr("Claude Chat talks to a small server inside Termux on this phone. Termux is open source; it is downloaded here if it is missing.",
            "Claude Chat, bu telefondaki Termux içindeki küçük bir sunucuyla konuşur. Termux açık kaynaklıdır; yoksa buradan indirilir.")) {
        when {
            !installed -> when (val d = dl) {
                is TermuxInstall.State.Idle -> BigButton(tr("Download Termux", "Termux'u indir")) { TermuxInstall.start(ctx) }
                is TermuxInstall.State.Failed -> {
                    BigButton(tr("Download Termux", "Termux'u indir")) { TermuxInstall.start(ctx) }
                    Text(d.why, fontSize = 12.sp, color = cs.error)
                }
                is TermuxInstall.State.Looking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                    Text(tr("Finding the latest release…", "Son sürüm aranıyor…"))
                }
                is TermuxInstall.State.Downloading -> Column {
                    LinearProgressIndicator(progress = { d.percent / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
                    Text("${d.percent}%", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                is TermuxInstall.State.Ready -> BigButton(tr("Install Termux", "Termux'u kur")) { TermuxInstall.install(ctx, d.file) }
            }
            !granted -> BigButton(tr("Allow Termux access", "Termux iznini ver")) { perm.launch(Termux.PERM) }
            bridge == 200 -> Text(tr("Connected.", "Bağlandı."), color = Color(0xFF4CAF50))
            bridge == 0 || bridge == -2 -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                Text(note.ifEmpty { tr("Checking…", "Kontrol ediliyor…") }, color = cs.onSurfaceVariant)
            }
            else -> {
                BigButton(tr("Start the connection", "Bağlantıyı başlat")) { Engine.connect() }
                BigOutlined(tr("Open Termux", "Termux'u aç")) { Termux.openTermux(ctx) }
                // when starting from here fails (Termux not allowed to take orders yet): one paste in Termux fixes it
                BigOutlined(tr("Repair once (paste in Termux)", "Bir kez onar (Termux'a yapıştır)")) {
                    SetupServer.command()?.let { clip.setText(AnnotatedString(it)); Termux.openTermux(ctx) }
                }
                err.takeIf { it.isNotBlank() }?.let { Text(it.take(300), fontSize = 12.sp, color = cs.onSurfaceVariant) }
            }
        }
        if (ok1) BigButton(tr("Next", "İleri"), onClick = next)
    }
}

@Composable
private fun ClaudeStep(ok1: Boolean, ok2: Boolean, auth: Engine.Auth?, onAuth: (Engine.Auth?) -> Unit, next: () -> Unit) {
    val title = tr("Sign in to Claude", "Claude'a giriş yap")
    val body = tr("Your browser opens Claude's sign-in page. Approve there and come back: nothing to copy.",
        "Tarayıcın Claude'un giriş sayfasını açar. Orada onayla ve geri dön: kopyalanacak bir şey yok.")
    if (ok1) SignInStep(2, "claude", title, body, ok2, auth?.email.orEmpty(), true, tr("Sign in with the browser", "Tarayıcıyla giriş yap"), big = true, onAuth = onAuth)
    else Step(2, title, false, body) { Text(tr("Needs step 1 first.", "Önce 1. adım gerekir."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (ok2) BigButton(tr("Next", "İleri"), onClick = next)
}

@Composable
private fun GitHubStep(ok1: Boolean, auth: Engine.Auth?, onAuth: (Engine.Auth?) -> Unit, back: () -> Unit, next: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gh = auth?.ghIn == true
    val title = tr("GitHub (optional)", "GitHub (isteğe bağlı)")
    val body = tr("Lets Claude push and pull for you. GitHub shows a short code: it is copied for you, paste it on the page that opens.",
        "Claude'un senin için push/pull yapmasını sağlar. GitHub kısa bir kod gösterir: senin için kopyalanır, açılan sayfaya yapıştır.")
    if (ok1 && auth?.ghInstalled != false) SignInStep(3, "gh", title, body, gh, auth?.ghUser.orEmpty(), true, tr("Sign in to GitHub", "GitHub'a giriş yap"), big = true, onAuth = onAuth)
    else Step(3, title, gh, body) {
        Text(if (ok1) tr("GitHub is not installed in Ubuntu. You can skip this.", "GitHub, Ubuntu'da kurulu değil. Bunu atlayabilirsin.")
            else tr("Needs step 1 first.", "Önce 1. adım gerekir."), fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = back, modifier = Modifier.weight(1f).height(56.dp)) { Text(tr("Back", "Geri")) }
        Button(onClick = next, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (gh) tr("Next", "İleri") else tr("Skip", "Atla")) }
    }
}

@Composable
private fun SummaryStep(ok1: Boolean, ok2: Boolean, gh: Boolean, ready: Boolean, done: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val notDone = tr("Not done", "Yapılmadı")
    Step(4, tr("All set", "Hazır"), ready, tr("Check the list, then open the chat.", "Listeye bak, sonra sohbeti aç.")) {
        CheckRow(tr("Termux and the connection", "Termux ve bağlantı"), ok1, notDone)
        CheckRow(tr("Signed in to Claude", "Claude'a giriş yapıldı"), ok2, notDone)
        CheckRow(tr("GitHub (optional)", "GitHub (isteğe bağlı)"), gh, tr("Skipped", "Atlandı"))
    }
    BigButton(tr("Done · Open chat", "Bitti · Sohbeti aç"), enabled = ready, onClick = done)
    if (!ready) Text(tr("Finish the required steps first.", "Önce zorunlu adımları bitir."), fontSize = 12.sp, color = cs.onSurfaceVariant)
}

@Composable
private fun CheckRow(label: String, ok: Boolean, otherwise: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ok) Icon(Icons.Filled.Check, null, Modifier.size(18.dp), tint = Color(0xFF4CAF50))
        else Icon(Icons.Filled.Remove, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Text(if (ok) tr("Done", "Tamam") else otherwise, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(text, fontSize = 16.sp) }
}

@Composable
private fun BigOutlined(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(text, fontSize = 16.sp) }
}
