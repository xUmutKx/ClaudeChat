package com.claudechat

/** A slash command shown in the palette. Local ones are handled by the app, the rest go to Claude Code. */
data class Cmd(val name: String, val desc: Int, val local: Boolean)

object Cmds {
    val localList = listOf(
        Cmd("model", R.string.cmd_model, true),
        Cmd("effort", R.string.cmd_effort, true),
        Cmd("mode", R.string.cmd_mode, true),
        Cmd("clear", R.string.cmd_clear, true),
        Cmd("cost", R.string.cmd_cost, true),
        Cmd("help", R.string.cmd_help, true),
        Cmd("settings", R.string.cmd_settings, true),
    )
    private val aliases = mapOf("new" to "clear", "reset" to "clear", "permissions" to "mode")

    private val known = mapOf(
        "compact" to R.string.cmd_compact, "init" to R.string.cmd_init, "review" to R.string.cmd_review,
        "security-review" to R.string.cmd_security, "code-review" to R.string.cmd_codereview,
        "simplify" to R.string.cmd_simplify, "loop" to R.string.cmd_loop, "schedule" to R.string.cmd_schedule,
        "context" to R.string.cmd_context, "usage" to R.string.cmd_usage, "doctor" to R.string.cmd_doctor,
        "mcp" to R.string.cmd_mcp, "agents" to R.string.cmd_agents, "config" to R.string.cmd_config,
        "fast" to R.string.cmd_fast, "rename" to R.string.cmd_rename, "goal" to R.string.cmd_goal,
        "insights" to R.string.cmd_insights, "debug" to R.string.cmd_debug, "verify" to R.string.cmd_verify,
        "batch" to R.string.cmd_batch, "run" to R.string.cmd_run, "ultrareview" to R.string.cmd_ultrareview,
    )

    /** Local commands first, then whatever the CLI reported in its init event. */
    fun all(): List<Cmd> {
        val localNames = localList.map { it.name }.toSet() + aliases.keys
        val remote = Prefs.slash.value.split(",").map { it.trim().removePrefix("/") }
            .filter { it.isNotEmpty() && it !in localNames }.distinct()
            .map { Cmd(it, known[it] ?: R.string.cmd_custom, false) }
        val fallback = known.keys.filter { k -> remote.none { it.name == k } && Prefs.slash.value.isEmpty() }
            .map { Cmd(it, known.getValue(it), false) }
        return localList + remote + fallback
    }

    fun matching(prefix: String): List<Cmd> = all().filter { it.name.startsWith(prefix.lowercase()) }

    val models = listOf("" to R.string.default_label, "fable" to 0, "opus" to 0, "sonnet" to 0, "haiku" to 0)
    val efforts = listOf("", "low", "medium", "high", "xhigh", "max")
    val modes = listOf("default", "acceptEdits", "plan", "bypassPermissions")

    /**
     * Runs a local command. Returns true if [text] was a local command (and is now handled);
     * [openOptions]/[openSettings] let the UI show its own screens.
     */
    fun runLocal(text: String, openOptions: () -> Unit, openSettings: () -> Unit): Boolean {
        if (!text.startsWith("/")) return false
        val parts = text.drop(1).trim().split(Regex("\\s+"), 2)
        val name = aliases[parts[0].lowercase()] ?: parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim().orEmpty()
        if (localList.none { it.name == name }) return false
        when (name) {
            "model" -> if (arg.isEmpty()) openOptions() else {
                Prefs.setChatModel(Engine.currentId.value, if (arg == "default") "" else arg)
                Engine.note(appStr(R.string.note_model, arg))
            }
            "effort" -> if (arg !in efforts && arg != "default") openOptions() else {
                Prefs.effort.value = if (arg == "default") "" else arg
                Engine.note(appStr(R.string.note_effort, arg.ifEmpty { appStr(R.string.default_label) }))
            }
            "mode" -> if (arg !in modes) openOptions() else {
                Prefs.mode.value = arg
                Engine.note(appStr(R.string.note_mode, arg))
            }
            "clear" -> Engine.newChat()
            "cost" -> Engine.note(appStr(R.string.note_cost, Engine.modelName.value.ifEmpty { "-" }))
            "help" -> Engine.note(appStr(R.string.help_title) + "\n" + all().joinToString("\n") { "/${it.name} — ${appStr(it.desc)}" })
            "settings" -> openSettings()
        }
        return true
    }
}
