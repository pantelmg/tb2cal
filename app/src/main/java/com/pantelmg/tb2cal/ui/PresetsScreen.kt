package com.pantelmg.tb2cal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.Fmt
import com.pantelmg.tb2cal.model.Settings

private const val MAX_PRESETS = 10

/** Edit the countdown presets shown under the hours and minutes on the TimeBlocks tab. */
@Composable
fun PresetsScreen(onBack: () -> Unit) {
    val settings by Repo.settings.collectAsState()
    val presets = settings.presets
    var hours by remember { mutableStateOf("0") }
    var minutes by remember { mutableStateOf("") }
    val h = hours.toIntOrNull()?.takeIf { it in 0..23 }
    val m = minutes.toIntOrNull()?.takeIf { it in 0..59 }
    val total = if (h != null && m != null) h * 60 + m else 0
    val canAdd = total > 0 && total !in presets && presets.size < MAX_PRESETS

    fun save(list: List<Int>) = Repo.updateSettings { it.copy(presets = list.distinct().sorted()) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Preset times", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            "Shown under the countdown on the TimeBlocks tab. Tap one there to set the time. Up to $MAX_PRESETS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            "Presets",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
        )
        Card(Modifier.fillMaxWidth()) {
            presets.forEachIndexed { i, mins ->
                if (i > 0) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(Fmt.duration(mins * 60_000L), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { save(presets - mins) }, enabled = presets.size > 1) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove")
                    }
                }
            }
        }

        Text(
            "Add a preset",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hours,
                onValueChange = { v -> hours = v.filter(Char::isDigit).take(2) },
                label = { Text("Hours") },
                singleLine = true,
                isError = h == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = minutes,
                onValueChange = { v -> minutes = v.filter(Char::isDigit).take(2) },
                label = { Text("Minutes") },
                singleLine = true,
                isError = minutes.isNotEmpty() && m == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(
            onClick = {
                save(presets + total)
                hours = "0"
                minutes = ""
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    presets.size >= MAX_PRESETS -> "Up to $MAX_PRESETS presets"
                    total > 0 && total in presets -> "${Fmt.duration(total * 60_000L)} is already a preset"
                    total > 0 -> "Add ${Fmt.duration(total * 60_000L)}"
                    else -> "Add"
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { save(Settings.DEFAULT_PRESETS) },
            enabled = presets != Settings.DEFAULT_PRESETS,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) { Text("Reset to default") }
    }
}
