package com.pantelmg.tb2cal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.data.Repo
import com.pantelmg.tb2cal.model.BlockType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBlocksScreen(onBack: () -> Unit) {
    val rows = remember { Repo.blocks.value.toMutableStateList() }
    var colorFor by remember { mutableStateOf<String?>(null) }

    fun persist() = Repo.setBlocks(rows.toList())

    fun update(id: String, f: (BlockType) -> BlockType) {
        val i = rows.indexOfFirst { it.id == id }
        if (i >= 0) {
            rows[i] = f(rows[i])
            persist()
        }
    }

    fun leave() {
        Repo.setBlocks(rows.map { it.copy(name = it.name.trim().ifEmpty { "untitled" }) })
        onBack()
    }

    BackHandler { leave() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit TimeBlocks") },
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    rows.add(BlockType(UUID.randomUUID().toString(), "", "1"))
                    persist()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Tap a color dot to set its calendar color.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(rows, key = { it.id }) { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(
                        b.colorKey, 34.dp,
                        modifier = Modifier.clickable { colorFor = b.id }
                    )
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = b.name,
                        onValueChange = { v -> update(b.id) { it.copy(name = v) } },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            rows.removeAll { it.id == b.id }
                            persist()
                        },
                        enabled = rows.size > 1
                    ) { Icon(Icons.Default.Delete, contentDescription = "Remove ${b.name}") }
                }
            }
        }
    }

    colorFor?.let { id ->
        ColorPickerDialog(
            current = rows.firstOrNull { it.id == id }?.colorKey,
            onPick = { key ->
                update(id) { it.copy(colorKey = key) }
                colorFor = null
            },
            onDismiss = { colorFor = null }
        )
    }
}
