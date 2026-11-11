package com.pantelmg.tb2cal.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.pantelmg.tb2cal.R
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.model.GoogleColors

@Composable
fun TB2CalTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF90CAF9), secondary = Color(0xFF80CBC4))
        else -> lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF00897B))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * The app's launcher symbol (blue tile, calendar with "TB"), drawn from the same vector as the
 * launcher icon. The foreground is enlarged and clipped the way launchers show adaptive icons.
 */
@Composable
fun AppSymbol(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF1565C0)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "TB2Cal",
            modifier = Modifier.requiredSize(size * 1.5f)
        )
    }
}

/** Filled dot in the block's calendar color; Outlined when "calendar default". */
@Composable
fun ColorDot(key: String?, size: Dp, modifier: Modifier = Modifier, selected: Boolean = false) {
    val c = GoogleColors.byKey(key)
    val shape = Modifier
        .size(size)
        .clip(CircleShape)
        .then(
            if (c != null) Modifier.background(Color(c.argb))
            else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
        )
    Box(modifier.then(shape), contentAlignment = Alignment.Center) {
        if (selected) {
            Icon(
                Icons.Default.Check, contentDescription = "Selected",
                tint = if (c != null) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

@Composable
fun ColorPickerDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(false) }
    if (custom) {
        val start = GoogleColors.byKey(current)?.argb?.toInt() ?: 0xFF1565C0.toInt()
        CustomColorDialog(
            initial = start,
            onPick = { key ->
                custom = false
                onPick(key)
            },
            onDismiss = { custom = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Calendar color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 11 preset colors, then "Custom color…" as the 12th tile.
                val tiles: List<String?> = GoogleColors.all.map { it.key } + listOf(null)
                tiles.chunked(4).forEach { row ->
                    Row {
                        row.forEach { key ->
                            val isCustomTile = key == null
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(64.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { if (isCustomTile) custom = true else onPick(key) }
                                    .padding(vertical = 6.dp)
                            ) {
                                if (isCustomTile) {
                                    // Shows the current custom color, or a rainbow if there is none.
                                    val cur = current?.takeIf { GoogleColors.isCustom(it) }
                                    if (cur != null) ColorDot(cur, 34.dp, selected = true)
                                    else Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.sweepGradient(
                                                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                                                )
                                            )
                                    )
                                    Text("Custom color…", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                } else {
                                    val gc = GoogleColors.byKey(key)!!
                                    ColorDot(gc.key, 34.dp, selected = current == gc.key)
                                    Text(gc.name, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

/** Hue across, light → strong → dark down, plus a gray column. Tap a cell or type R, G, B. */
@Composable
fun CustomColorDialog(initial: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(((initial shr 16) and 0xFF).toString()) }
    var g by remember { mutableStateOf(((initial shr 8) and 0xFF).toString()) }
    var b by remember { mutableStateOf((initial and 0xFF).toString()) }
    val ri = r.toIntOrNull()?.takeIf { it in 0..255 }
    val gi = g.toIntOrNull()?.takeIf { it in 0..255 }
    val bi = b.toIntOrNull()?.takeIf { it in 0..255 }
    val valid = ri != null && gi != null && bi != null
    val preview = if (valid) Color(ri!!, gi!!, bi!!) else Color.Transparent

    val cols = 19 // 18 hues + 1 gray column
    val rows = 10
    val cells = remember {
        List(rows) { row ->
            List(cols) { col ->
                if (col == cols - 1) {
                    val v = 1f - row / (rows - 1f)
                    android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, v))
                } else {
                    val hue = col * 360f / (cols - 1)
                    val (sat, v) = if (row < 5) (row + 1) / 5f to 1f else 1f to (1f - (row - 4) * 0.15f)
                    android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, v))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        confirmButton = {
            TextButton(onClick = { onPick(GoogleColors.customKey(ri!!, gi!!, bi!!)) }, enabled = valid) { Text("Use") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(cols / rows.toFloat())
                        .clip(MaterialTheme.shapes.small)
                        .pointerInput(Unit) {
                            detectTapGestures { p ->
                                val col = (p.x / size.width * cols).toInt().coerceIn(0, cols - 1)
                                val row = (p.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                                val c = cells[row][col]
                                r = ((c shr 16) and 0xFF).toString()
                                g = ((c shr 8) and 0xFF).toString()
                                b = (c and 0xFF).toString()
                            }
                        }
                ) {
                    val cw = size.width / cols
                    val ch = size.height / rows
                    for (row in 0 until rows) for (col in 0 until cols) {
                        drawRect(Color(cells[row][col]), topLeft = Offset(col * cw, row * ch), size = Size(cw + 0.5f, ch + 0.5f))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (valid) GoogleColors.customKey(ri!!, gi!!, bi!!) else "Each value: 0–255",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("R", r) { v: String -> r = v }, Triple("G", g) { v: String -> g = v }, Triple("B", b) { v: String -> b = v })
                        .forEach { (label, value, set) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v -> set(v.filter(Char::isDigit).take(3)) },
                                label = { Text(label) },
                                singleLine = true,
                                isError = value.toIntOrNull()?.let { it !in 0..255 } ?: true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
            }
        }
    )
}
