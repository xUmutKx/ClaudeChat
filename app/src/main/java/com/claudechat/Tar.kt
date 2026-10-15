package com.claudechat

import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream

/** A .tar.gz unpacker for the Linux userland: Android has no tar binary an app may run. Pure JVM, so it is tested off the phone. */
object Tar {
    /**
     * Unpacks [src] (gzip-compressed tar) into [dest]: folders, files, symlinks, and hard links (copied).
     * Device nodes and fifos are skipped (proot binds /dev). Entries that would land outside [dest] are skipped.
     * [onEntry] gets each path and returns false to stop.
     */
    fun untgz(src: InputStream, dest: File, onEntry: (String) -> Boolean = { true }) {
        dest.mkdirs()
        val base = dest.canonicalPath
        val inp = BufferedInputStream(GZIPInputStream(src, 1 shl 16), 1 shl 16)
        val h = ByteArray(512)
        val buf = ByteArray(1 shl 16)
        var longName: String? = null
        var longLink: String? = null
        while (true) {
            if (!full(inp, h)) break
            if (h.all { it == 0.toByte() }) break
            val size = num(h, 124, 12)
            val type = h[156].toInt().toChar()
            if (type == 'L' || type == 'K' || type == 'x') {
                val data = String(read(inp, size), Charsets.UTF_8).trimEnd('\u0000')
                when (type) {
                    'L' -> longName = data
                    'K' -> longLink = data
                    else -> pax(data).let { m -> m["path"]?.let { longName = it }; m["linkpath"]?.let { longLink = it } }
                }
                pad(inp, size); continue
            }
            val prefix = str(h, 345, 155)
            val name = longName ?: str(h, 0, 100).let { if (prefix.isNotEmpty()) "$prefix/$it" else it }
            val link = longLink ?: str(h, 157, 100)
            longName = null; longLink = null
            val mode = num(h, 100, 8).toInt()
            val rel = clean(name)
            if (rel == null || !onEntry(rel)) {
                skip(inp, size); pad(inp, size)
                if (rel == null) continue else return
            }
            val f = File(dest, rel)
            // a symlinked parent could point anywhere: the folder it really lands in must still be inside dest
            val parent = f.parentFile!!
            parent.mkdirs()
            if (parent.canonicalPath != base && !parent.canonicalPath.startsWith("$base/")) { skip(inp, size); pad(inp, size); continue }
            when (type) {
                '5' -> f.mkdirs()
                '2' -> { remove(f); Files.createSymbolicLink(f.toPath(), Paths.get(link)) }
                '1' -> {
                    val t = clean(link)?.let { File(dest, it) }
                    if (t != null && Files.exists(t.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                        remove(f); Files.copy(t.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS)
                    }
                }
                '0', '\u0000', '7' -> {
                    remove(f)
                    f.outputStream().use { out ->
                        var left = size
                        while (left > 0) {
                            val n = inp.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                            if (n < 0) throw java.io.EOFException("archive ends inside $rel")
                            out.write(buf, 0, n); left -= n
                        }
                    }
                    if (mode and 0b001_001_001 != 0) f.setExecutable(true, false)
                }
                else -> skip(inp, size)
            }
            pad(inp, size)
        }
    }

    /** "./usr/bin/x" -> "usr/bin/x"; null for the root itself, absolute names and anything with "..". */
    private fun clean(n: String): String? {
        val parts = n.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun remove(f: File) { try { Files.deleteIfExists(f.toPath()) } catch (e: Exception) { } }

    private fun pax(s: String): Map<String, String> = s.lineSequence().mapNotNull { l ->
        val kv = l.substringAfter(' ', "")
        if (!kv.contains('=')) null else kv.substringBefore('=') to kv.substringAfter('=')
    }.toMap()

    private fun str(h: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && h[end] != 0.toByte()) end++
        return String(h, off, end - off, Charsets.UTF_8)
    }

    private fun num(h: ByteArray, off: Int, len: Int): Long {
        if (h[off].toInt() and 0x80 != 0) { // GNU base-256 for big values
            var v = 0L
            for (i in off + 1 until off + len) v = (v shl 8) or (h[i].toLong() and 0xff)
            return v
        }
        return str(h, off, len).trim().ifEmpty { "0" }.toLong(8)
    }

    private fun full(inp: InputStream, b: ByteArray): Boolean {
        var got = 0
        while (got < b.size) {
            val n = inp.read(b, got, b.size - got)
            if (n < 0) { if (got == 0) return false; throw java.io.EOFException("archive cut short") }
            got += n
        }
        return true
    }

    private fun read(inp: InputStream, size: Long): ByteArray { val b = ByteArray(size.toInt()); full(inp, b); return b }

    private fun skip(inp: InputStream, size: Long) {
        var left = size
        while (left > 0) { val n = inp.skip(left); if (n <= 0) { if (inp.read() < 0) throw java.io.EOFException(); left-- } else left -= n }
    }

    private fun pad(inp: InputStream, size: Long) = skip(inp, (512 - size % 512) % 512)
}
