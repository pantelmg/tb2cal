package com.pantelmg.tb2cal.data

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * Keeps the chosen alert sound playable for good.
 *
 * A sound picked from your own files is only readable for a short while (Android's access grant
 * expires, or the media library renumbers the file). Afterwards the app sees just a number and
 * plays nothing. So such sounds are copied into TB2Cal's private storage right away.
 * Built-in system sounds and "Default" stay as links, because they're always readable.
 */
object SoundStore {
    private const val DIR = "sounds"

    data class Stored(val uri: String, val title: String)

    /** Must run off the main thread, straight after the user picked the sound. */
    fun import(ctx: Context, picked: Uri): Stored {
        val ringtoneTitle = runCatching { RingtoneManager.getRingtone(ctx, picked)?.getTitle(ctx) }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }

        val systemSound = picked.authority == "settings" ||
            (picked.authority == "media" && picked.path?.startsWith("/internal/") == true)
        if (systemSound) {
            cleanup(ctx, keep = null)
            return Stored(picked.toString(), ringtoneTitle ?: displayName(ctx, picked) ?: "System sound")
        }

        val title = displayName(ctx, picked) ?: ringtoneTitle ?: "Custom sound"

        val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
        val ext = title.substringAfterLast('.', "").takeIf { it.length in 1..5 } ?: "audio"
        val dest = File(dir, "alert_${System.currentTimeMillis()}.$ext")
        val input = ctx.contentResolver.openInputStream(picked) ?: error("Couldn't read that sound file.")
        input.use { inp -> dest.outputStream().use { out -> inp.copyTo(out) } }
        val stored = Uri.fromFile(dest).toString()
        cleanup(ctx, keep = stored)
        return Stored(stored, title.substringBeforeLast('.').ifBlank { title })
    }

    /** Deletes copied sounds that are no longer selected. */
    fun cleanup(ctx: Context, keep: String?) {
        File(ctx.filesDir, DIR).listFiles()?.forEach { f ->
            if (Uri.fromFile(f).toString() != keep) f.delete()
        }
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
}
