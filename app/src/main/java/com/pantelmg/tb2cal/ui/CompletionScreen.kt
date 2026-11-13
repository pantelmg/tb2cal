package com.pantelmg.tb2cal.ui

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.calendar.CalendarWriter
import com.pantelmg.tb2cal.calendar.Registrar
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.CompletedBlock
import com.pantelmg.tb2cal.service.AlertService
import com.pantelmg.tb2cal.service.Notifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun CompletionScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pending by Repo.pending.collectAsState()
    val scope = rememberCoroutineScope()
    val status = remember { mutableStateMapOf<String, String>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val permWaiter = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }

    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permWaiter[0]?.complete(CalendarWriter.hasPermission(ctx))
    }

    suspend fun askCalendar(): Boolean {
        if (CalendarWriter.hasPermission(ctx)) return true
        val d = CompletableDeferred<Boolean>()
        permWaiter[0] = d
        calPerm.launch(CalendarWriter.PERMS)
        return d.await()
    }

    fun transfer(targets: List<CompletedBlock>) {
        scope.launch {
            val todo = targets.filter { !it.registered && busy[it.id] != true }
            if (todo.isEmpty()) return@launch
            todo.forEach {
                busy[it.id] = true
                failed[it.id] = false
                status[it.id] = "Adding to the calendar…"
            }
            try {
                if (!askCalendar()) {
                    todo.forEach {
                        status[it.id] = "Calendar access is needed. Tap Retry to allow it."
                        failed[it.id] = true
                    }
                    return@launch
                }
                for (c in todo) {
                    when (val r = Registrar.register(ctx, c.id)) {
                        is Registrar.Result.Done -> status.remove(c.id)
                        is Registrar.Result.Failed -> {
                            status[c.id] = r.reason
                            failed[c.id] = true
                        }
                    }
                }
            } finally {
                todo.forEach { busy[it.id] = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        AlertService.stop(ctx, keepNotification = false)
        Notifications.cancelCompleted(ctx)
        transfer(Repo.pending.value)
    }

    val df = DateFormat.getMediumDateFormat(ctx)
    val tf = DateFormat.getTimeFormat(ctx)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("TimeBlock completed", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (pending.isEmpty()) {
            Text("Nothing waiting to be registered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        pending.forEach { c ->
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(c.colorKey, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(c.label, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${df.format(Date(c.startWallMs))} · ${tf.format(Date(c.startWallMs))} – ${tf.format(Date(c.endWallMs))} · ${c.mode.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    if (c.registered) {
                        Text(
                            "✓ Registered",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        c.eventId?.let { eventId ->
                            TextButton(onClick = {
                                // Always asks which calendar app to open it in (every one that can, except TB2Cal).
                                runCatching {
                                    val view = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
                                    ctx.startActivity(
                                        Intent.createChooser(view, "Open in another calendar app")
                                            .putExtra(
                                                Intent.EXTRA_EXCLUDE_COMPONENTS,
                                                arrayOf(android.content.ComponentName(ctx, MainActivity::class.java))
                                            )
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }) { Text("Open in another calendar app…") }
                        }
                    } else {
                        Text(status[c.id] ?: "Waiting for the calendar…", style = MaterialTheme.typography.bodyMedium)
                        if (failed[c.id] == true) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { transfer(listOf(c)) }) { Text("Retry") }
                                TextButton(onClick = { Repo.removePending(c.id) }) { Text("Discard") }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                Repo.removeTransferred()
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("DONE") }
    }
}
