package com.claudechat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The chat is always drawn at once. The setup wizard (SetupWizard below) is no longer opened by itself:
 * the phone is already set up, and the chat shows its own connection state and buttons.
 */
@Composable
fun SetupGate(content: @Composable () -> Unit) = content()

@Composable
private fun SetupWizard(mode: String, installed: Boolean, granted: Boolean, bridge: Int, auth: Engine.Auth?, ready: Boolean,
                        rootGranted: Boolean, rootBin: Boolean, apiOk: Boolean,
                        onAuth: (Engine.Auth?) -> Unit, onApi: (Boolean) -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ok1 = installed && granted && bridge == 200
    val ok2 = ok1 && auth?.claudeIn == true
    val rootOk = rootGranted && rootBin
    val total = if (mode == SetupMode.TERMUX) 5 else 3   // choose, then the mode's steps (summary last)
    var idx by rememberSaveable { mutableStateOf(0) }
    // a required step that turns green moves on by itself; going back to it does not re-trigger this
    LaunchedEffect(ok1, ok2, rootOk, apiOk, mode) {
        if (mode == SetupMode.TERMUX) { if (ok1 && idx == 1) idx = 2; if (ok2 && idx == 2) idx = 3 }
        else if (mode == SetupMode.ROOT && rootOk && idx == 1) idx = 2
        else if (mode == SetupMode.API && apiOk && idx == 1) idx = 2
    }

    Column(Modifier.fillMaxSize().background(cs.background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Set up Claude on this phone", "Claude'u bu telefona kur"), fontFamily = FontFamily.Serif, fontSize = 22.sp)
            LinearProgressIndicator(progress = { (idx + 1) / total.toFloat() }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
            Text(tr("Step ${idx + 1} of $total", "Adım ${idx + 1} / $total"), fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                idx == 0 -> ModeStep(mode, { Prefs.setupMode.value = it }) { idx = 1 }
                mode == SetupMode.ROOT && idx == 1 -> RootStep(rootGranted, rootBin) { idx = 2 }
                mode == SetupMode.API && idx == 1 -> ApiStep(apiOk, onApi) { idx = 2 }
                mode == SetupMode.TERMUX && idx == 1 -> TermuxStep(installed, granted, bridge, ok1) { idx = 2 }
                mode == SetupMode.TERMUX && idx == 2 -> ClaudeStep(ok1, ok2, auth, onAuth) { idx = 3 }
                mode == SetupMode.TERMUX && idx == 3 -> GitHubStep(ok1, auth, onAuth, { idx = 2 }) { idx = 4 }
                else -> SummaryStep(mode, ok1, ok2, auth?.ghIn == true, rootOk, apiOk, ready, onDone)
            }
        }
    }
}

@Composable
private fun ModeStep(mode: String, pick: (String) -> Unit, next: () -> Unit) {
    Step(1, tr("How should Claude run?", "Claude nasıl çalışsın?"), false, tr("Pick one. The steps after this depend on it.", "Birini seç. Bundan sonraki adımlar buna göre değişir.")) {
        ModeCard(tr("Root (no Termux)", "Root (Termux yok)"),
            tr("For rooted phones. The claude program runs directly through su. Needs root and the claude file on this phone.",
                "Rootlu telefonlar için. claude programı doğrudan su ile çalışır. Root ve bu telefondaki claude dosyası gerekir."),
            mode == SetupMode.ROOT) { pick(SetupMode.ROOT) }
        ModeCard(tr("API key", "API anahtarı"),
            tr("Paste an Anthropic API key. The app talks to Anthropic directly. API use is paid.",
                "Bir Anthropic API anahtarı yapıştır. Uygulama doğrudan Anthropic ile konuşur. API kullanımı ücretlidir."),
            mode == SetupMode.API) { pick(SetupMode.API) }
        ModeCard(tr("Termux and your Claude login", "Termux ve Claude girişin"),
            tr("Claude Code runs inside Termux, through the small bridge. Uses your Claude login.",
                "Claude Code, küçük köprü üzerinden Termux içinde çalışır. Claude girişini kullanır."),
            mode == SetupMode.TERMUX) { pick(SetupMode.TERMUX) }
    }
    BigButton(tr("Next", "İleri"), onClick = next)
}

@Composable
private fun ModeCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurface
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (selected) cs.primaryContainer else cs.surfaceVariant)
        .clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 16.sp, color = fg)
        Text(body, fontSize = 13.sp, color = if (selected) fg else cs.onSurfaceVariant)
    }
}

@Composable
private fun RootStep(rootGranted: Boolean, rootBin: Boolean, next: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ok = rootGranted && rootBin
    Step(2, tr("Root access", "Root erişimi"), ok,
        tr("This mode needs a rooted phone. Claude runs straight through su, without Termux. The claude program must be at ${RootMode.BIN}. It uses the login of the root account on this phone, which is separate from the Termux login.",
            "Bu mod rootlu telefon ister. Claude, Termux olmadan doğrudan su ile çalışır. claude programı şurada olmalı: ${RootMode.BIN}. Termux girişinden ayrı olan, bu telefondaki root hesabının girişini kullanır.")) {
        CheckRow(tr("Root granted (su)", "Root izni (su)"), rootGranted, tr("Not granted", "Verilmedi"))
        CheckRow(tr("claude program found", "claude programu bulundu"), rootBin, tr("Not found", "Bulunamadı"))
        if (!rootGranted) Text(tr("Allow su for Claude Chat in your root manager (Magisk or KernelSU). This check repeats by itself.",
            "Root yöneticinde (Magisk veya KernelSU) Claude Chat'e su izni ver. Bu kontrol kendiliğinden tekrarlanır."), fontSize = 12.sp, color = cs.onSurfaceVariant)
        else if (!rootBin) Text(tr("Root mode is not available: the claude program is missing at that path. Install Claude Code for root, or pick another mode.",
            "Root modu kullanılamıyor: claude programı bu yolda yok. Root için Claude Code'u kur ya da başka bir mod seç."), fontSize = 12.sp, color = cs.error)
    }
    if (ok) BigButton(tr("Next", "İleri"), onClick = next)
}

@Composable
private fun ApiStep(apiOk: Boolean, onApi: (Boolean) -> Unit, next: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    Step(2, tr("Anthropic API key", "Anthropic API anahtarı"), apiOk,
        tr("Claude answers straight from the Anthropic API, with no Termux. The key stays on this phone and is sent only to Anthropic. API use is paid: every message is billed to your Anthropic account by the token, separately from a Claude subscription.",
            "Claude, Termux olmadan doğrudan Anthropic API'sinden cevap verir. Anahtar bu telefonda kalır ve yalnızca Anthropic'e gönderilir. API kullanımı ücretlidir: her mesaj Anthropic hesabına token başına ücretlendirilir, Claude aboneliğinden ayrıdır.")) {
        OutlinedTextField(value = text, onValueChange = { text = it.trim() }, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(tr("Paste the key here", "Anahtarı buraya yapıştır")) }, visualTransformation = PasswordVisualTransformation())
        Text(tr("Paid: each message is billed. The test sends one short request, which costs less than one US cent.",
            "Ücretli: her mesaj ücretlendirilir. Test tek ve kısa bir istek gönderir, bu bir ABD sentinden azdır."), fontSize = 12.sp, color = cs.onSurfaceVariant)
        BigButton(if (testing) tr("Testing…", "Test ediliyor…") else tr("Test key", "Anahtarı test et"),
            enabled = !testing && (text.isNotEmpty() || AnthropicApi.key().isNotEmpty())) {
            scope.launch {
                if (text.isNotEmpty()) { AnthropicApi.setKey(text); text = "" }
                testing = true; onApi(false); msg = ""
                val err = withContext(Dispatchers.IO) { AnthropicApi.test() }
                testing = false
                bad = err != null
                msg = err ?: tr("The key works. You can continue.", "Anahtar çalışıyor. Devam edebilirsin.")
                onApi(err == null)
            }
        }
        if (msg.isNotEmpty()) Text(msg, fontSize = 13.sp, color = if (bad) cs.error else Color(0xFF4CAF50))
    }
    if (apiOk) BigButton(tr("Next", "İleri"), onClick = next)
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
    Step(2, tr("Termux and the connection", "Termux ve bağlantı"), ok1,
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
    if (ok1) SignInStep(3, "claude", title, body, ok2, auth?.email.orEmpty(), true, tr("Sign in with the browser", "Tarayıcıyla giriş yap"), big = true, onAuth = onAuth)
    else Step(3, title, false, body) { Text(tr("Needs step 1 first.", "Önce 1. adım gerekir."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (ok2) BigButton(tr("Next", "İleri"), onClick = next)
}

@Composable
private fun GitHubStep(ok1: Boolean, auth: Engine.Auth?, onAuth: (Engine.Auth?) -> Unit, back: () -> Unit, next: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val gh = auth?.ghIn == true
    val title = tr("GitHub (optional)", "GitHub (isteğe bağlı)")
    val body = tr("Lets Claude push and pull for you. GitHub shows a short code: it is copied for you, paste it on the page that opens.",
        "Claude'un senin için push/pull yapmasını sağlar. GitHub kısa bir kod gösterir: senin için kopyalanır, açılan sayfaya yapıştır.")
    if (ok1 && auth?.ghInstalled != false) SignInStep(4, "gh", title, body, gh, auth?.ghUser.orEmpty(), true, tr("Sign in to GitHub", "GitHub'a giriş yap"), big = true, onAuth = onAuth)
    else Step(4, title, gh, body) {
        Text(if (ok1) tr("GitHub is not installed in Ubuntu. You can skip this.", "GitHub, Ubuntu'da kurulu değil. Bunu atlayabilirsin.")
            else tr("Needs step 1 first.", "Önce 1. adım gerekir."), fontSize = 12.sp, color = cs.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = back, modifier = Modifier.weight(1f).height(56.dp)) { Text(tr("Back", "Geri")) }
        Button(onClick = next, modifier = Modifier.weight(1f).height(56.dp)) { Text(if (gh) tr("Next", "İleri") else tr("Skip", "Atla")) }
    }
}

@Composable
private fun SummaryStep(mode: String, ok1: Boolean, ok2: Boolean, gh: Boolean, rootOk: Boolean, apiOk: Boolean, ready: Boolean, done: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val notDone = tr("Not done", "Yapılmadı")
    Step(if (mode == SetupMode.TERMUX) 5 else 3, tr("All set", "Hazır"), ready, tr("Check the list, then open the chat.", "Listeye bak, sonra sohbeti aç.")) {
        when (mode) {
            SetupMode.ROOT -> CheckRow(tr("Root and the claude program", "Root ve claude programı"), rootOk, notDone)
            SetupMode.API -> CheckRow(tr("API key saved and tested", "API anahtarı kaydedildi ve test edildi"), apiOk, notDone)
            else -> {
                CheckRow(tr("Termux and the connection", "Termux ve bağlantı"), ok1, notDone)
                CheckRow(tr("Signed in to Claude", "Claude'a giriş yapıldı"), ok2, notDone)
                CheckRow(tr("GitHub (optional)", "GitHub (isteğe bağlı)"), gh, tr("Skipped", "Atlandı"))
            }
        }
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
