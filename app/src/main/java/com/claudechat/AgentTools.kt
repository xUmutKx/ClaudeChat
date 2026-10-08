package com.claudechat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * File and command tools for the other AIs, run inside the app: no bridge and no network listener. Files are limited to one folder you pick
 * (Settings > AI), paths cannot leave it, and anything that changes something asks you first unless the permission mode says otherwise.
 */
object AgentTools {
    fun root(): File = File(Prefs.agentDir.value.ifBlank { "/sdcard/Download/projects" })

    private fun resolve(p: String): File? {
        val base = root().canonicalFile
        val f = (if (p.startsWith("/")) File(p) else File(base, p)).canonicalFile
        return if (f.path == base.path || f.path.startsWith(base.path + "/")) f else null
    }

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>) = JSONObject().put("type", "function").put("function",
        JSONObject().put("name", name).put("description", desc).put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required))))

    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun num(desc: String) = JSONObject().put("type", "integer").put("description", desc)

    /** Read-only tools always; changing ones unless the mode is plan. */
    fun specs(mode: String): JSONArray {
        val a = JSONArray()
        a.put(fn("Read", "Read a text file. Returns numbered lines.", JSONObject().put("file_path", str("Path inside the project folder")).put("offset", num("First line to skip to (0-based)")).put("limit", num("Max lines")), listOf("file_path")))
        a.put(fn("LS", "List a folder.", JSONObject().put("path", str("Folder, default the project folder")), emptyList()))
        a.put(fn("Grep", "Search text in files (regex, case-sensitive).", JSONObject().put("pattern", str("Regular expression")).put("path", str("Folder or file")).put("glob", str("File name filter like *.kt")), listOf("pattern")))
        if (mode != "plan") {
            a.put(fn("Write", "Create or overwrite a file.", JSONObject().put("file_path", str("Path")).put("content", str("Whole file content")), listOf("file_path", "content")))
            a.put(fn("Edit", "Replace exact text in a file. Read the file first and copy old_string exactly.", JSONObject().put("file_path", str("Path")).put("old_string", str("Exact text to replace")).put("new_string", str("New text")).put("replace_all", JSONObject().put("type", "boolean")), listOf("file_path", "old_string", "new_string")))
            a.put(fn("Bash", "Run a shell command in the project folder (the phone's shell).", JSONObject().put("command", str("Command line")).put("timeout", num("Seconds, default 120")), listOf("command")))
        }
        return a
    }

    fun needsAsk(tool: String, mode: String) = when (tool) {
        "Write", "Edit" -> mode == "default"
        "Bash" -> mode != "bypassPermissions"
        else -> false
    }

    private val BAD = Regex("(^|[;&|\\s])(rm\\s+-[a-z]*r[a-z]*\\s+(/|~|/sdcard|/storage)\\s*($|[;&|])|mkfs|dd\\s+[^|;]*of=/dev|shutdown|reboot|poweroff)")

    private fun cap(t: String, n: Int) = if (t.length > n) t.take(n) + "\n…(cut, ${t.length - n} more characters)" else t

    /** Runs one tool; the returned text goes back to the model (errors included, so it can correct itself). */
    fun run(name: String, a: JSONObject, mode: String): String {
        try {
            if (!root().isDirectory) return "The project folder ${root().path} does not exist or is not readable. Give the app 'All files access' and set the folder in Settings > AI."
            when (name) {
                "Read" -> {
                    val f = resolve(a.optString("file_path")) ?: return "Path is outside the project folder."
                    if (!f.isFile) return "${f.path} is not a file."
                    if (f.length() > 3_000_000) return "File is too large (${f.length()} bytes). Use Grep or Bash."
                    val lines = f.readLines()
                    val off = a.optInt("offset", 0).coerceAtLeast(0); val lim = a.optInt("limit", 2000).coerceIn(1, 2000)
                    return cap(lines.drop(off).take(lim).mapIndexed { i, l -> (off + i + 1).toString().padStart(5) + "\t" + l }.joinToString("\n"), 120_000)
                }
                "LS" -> {
                    val d = resolve(a.optString("path", ".").ifBlank { "." }) ?: return "Path is outside the project folder."
                    val list = d.listFiles() ?: return "${d.path} is not a folder."
                    return d.path + "\n" + list.sortedBy { it.name }.take(500).joinToString("\n") { if (it.isDirectory) it.name + "/" else it.name }
                }
                "Grep" -> {
                    val d = resolve(a.optString("path", ".").ifBlank { "." }) ?: return "Path is outside the project folder."
                    val re = try { Regex(a.optString("pattern")) } catch (e: Exception) { return "Bad regular expression: ${e.message}" }
                    val glob = a.optString("glob").takeIf { it.isNotBlank() }?.let { Regex(it.replace(".", "\\.").replace("*", ".*").replace("?", ".")) }
                    val out = StringBuilder(); var n = 0
                    d.walkTopDown().onEnter { it.name != "node_modules" && it.name != ".git" && it.name != "build" && it.name != ".gradle" }
                        .filter { it.isFile && it.length() < 1_000_000 && (glob == null || glob.matches(it.name)) }.forEach { f ->
                            if (n >= 60) return@forEach
                            try { f.useLines { ls -> ls.forEachIndexed { i, l -> if (n < 60 && re.containsMatchIn(l)) { out.append(f.path).append(':').append(i + 1).append(": ").append(l.take(200)).append('\n'); n++ } } } } catch (e: Exception) { }
                        }
                    return if (out.isEmpty()) "No matches." else cap(out.toString(), 30_000)
                }
                "Write" -> {
                    if (mode == "plan") return "Plan mode: writing is off."
                    val f = resolve(a.optString("file_path")) ?: return "Path is outside the project folder."
                    f.parentFile?.mkdirs(); f.writeText(a.optString("content"))
                    return "Wrote ${f.path}"
                }
                "Edit" -> {
                    if (mode == "plan") return "Plan mode: editing is off."
                    val f = resolve(a.optString("file_path")) ?: return "Path is outside the project folder."
                    if (!f.isFile) return "${f.path} is not a file."
                    val t = f.readText(); val o = a.optString("old_string")
                    if (o.isEmpty()) return "old_string is empty."
                    val n = t.split(o).size - 1
                    if (n == 0) return "old_string was not found in ${f.path}. Read the file again and copy the text exactly."
                    val all = a.optBoolean("replace_all", false)
                    if (n > 1 && !all) return "old_string appears $n times; add more context or set replace_all."
                    f.writeText(if (all) t.replace(o, a.optString("new_string")) else t.replaceFirst(o, a.optString("new_string")))
                    return "Edited ${f.path}" + if (n > 1) " ($n places)" else ""
                }
                "Bash" -> {
                    if (mode == "plan") return "Plan mode: commands are off."
                    val c = a.optString("command")
                    if (BAD.containsMatchIn(c)) return "Refused: this command looks destructive."
                    val args = if (Prefs.agentRoot.value) listOf("su", "-c", "cd '${root().path}' && $c") else listOf("sh", "-c", c)
                    val p = ProcessBuilder(args).directory(root()).redirectErrorStream(true).start()
                    val out = StringBuilder()
                    val t = Thread { try { p.inputStream.bufferedReader().forEachLine { if (out.length < 60_000) out.appendLine(it) } } catch (e: Exception) { } }.also { it.start() }
                    val secs = a.optLong("timeout", 120).coerceIn(5, 600)
                    if (!p.waitFor(secs, TimeUnit.SECONDS)) { p.destroyForcibly(); t.join(1000); return cap(out.toString(), 30_000) + "\n[timed out after ${secs}s]" }
                    t.join(2000)
                    val code = p.exitValue()
                    return cap(out.toString().trim().ifEmpty { "(no output)" }, 30_000) + if (code != 0) "\n[exit code $code]" else ""
                }
                else -> return "Unknown tool $name."
            }
        } catch (e: Exception) { return e.message ?: e.javaClass.simpleName }
    }
}
