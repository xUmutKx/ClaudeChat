package com.claudechat

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Hands the setup / repair script to Termux, so the user pastes one short line instead of a screenful of commands:
 *     bash -c "$(curl -fsSL http://127.0.0.1:PORT/s/CODE)"
 * It listens on 127.0.0.1 only, serves nothing but that script, and only for the random CODE of the line that was just copied
 * (valid for 15 minutes), because the script holds the bridge token.
 */
object SetupServer {
    private var server: ServerSocket? = null
    @Volatile private var code = ""
    @Volatile private var until = 0L
    @Volatile var port = 0
        private set

    @Synchronized fun start(): Boolean {
        if (server?.isClosed == false) return true
        return try {
            val s = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            server = s; port = s.localPort
            Thread({
                while (!s.isClosed) {
                    try { val c = s.accept(); Thread({ serve(c) }, "setup-serve").apply { isDaemon = true; start() } } catch (e: Exception) { break }
                }
            }, "setup-server").apply { isDaemon = true; start() }
            true
        } catch (e: Exception) { false }
    }

    /** The line to paste into Termux (a fresh code each time), or null when the local server could not start. */
    fun command(): String? {
        if (!start()) return null
        code = Prefs.randomToken().take(16)
        until = System.currentTimeMillis() + 15 * 60_000L
        return "bash -c \"\$(curl -fsSL http://127.0.0.1:$port/s/$code)\""
    }

    /** assets/setup.sh with this phone's bridge, token, port and distro filled in. */
    private fun script(c: Context): String = c.assets.open("setup.sh").bufferedReader().use { it.readText() }
        .replace("@BRIDGE_B64@", Termux.bridgeB64(c))
        .replace("@TOKEN@", Termux.cleanToken())
        .replace("@PORT@", Termux.portStr())
        .replace("@DISTRO@", Termux.distroName())

    private fun serve(sock: Socket) {
        try {
            sock.use { s ->
                s.soTimeout = 5000
                val line = BufferedReader(InputStreamReader(s.getInputStream())).readLine().orEmpty()
                val path = line.split(' ').getOrNull(1).orEmpty()
                val ok = code.isNotEmpty() && path == "/s/$code" && System.currentTimeMillis() < until
                val body = if (ok) script(App.ctx).toByteArray() else ByteArray(0)
                val head = (if (ok) "HTTP/1.1 200 OK" else "HTTP/1.1 404 Not Found") + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                s.getOutputStream().apply { write(head.toByteArray()); write(body); flush() }
            }
        } catch (e: Exception) { }
    }
}
