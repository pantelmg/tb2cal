package com.pantelmg.tb2cal.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.drawscope.Stroke
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
                tint = if (c != null) Color(GoogleColors.textOn(c.argb.toInt())) else MaterialTheme.colorScheme.onSurface,
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

/**
 * Hue across, light → strong → dark down, plus a gray column. Touch and slide on the map (the color follows the
 * finger), or type R, G, B (0–255) or a hex code. A marker in the chosen color, with a ring in its negative color,
 * shows where it is on the map.
 */
@Composable
fun CustomColorDialog(initial: Int, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    fun hexOf(c: Int) = String.format(java.util.Locale.ROOT, "%06X", c and 0xFFFFFF)
    var r by remember { mutableStateOf(((initial shr 16) and 0xFF).toString()) }
    var g by remember { mutableStateOf(((initial shr 8) and 0xFF).toString()) }
    var b by remember { mutableStateOf((initial and 0xFF).toString()) }
    var hex by remember { mutableStateOf(hexOf(initial)) }
    // Map cell last touched (row, col); null = typed, so the marker goes to the nearest cell.
    var touched by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val ri = r.toIntOrNull()?.takeIf { it in 0..255 }
    val gi = g.toIntOrNull()?.takeIf { it in 0..255 }
    val bi = b.toIntOrNull()?.takeIf { it in 0..255 }
    val chosen: Int? = if (ri != null && gi != null && bi != null) (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi else null

    fun setRgb(c: Int) {
        r = ((c shr 16) and 0xFF).toString()
        g = ((c shr 8) and 0xFF).toString()
        b = (c and 0xFF).toString()
        hex = hexOf(c)
    }

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
    val marker: Pair<Int, Int>? = remember(chosen, touched) {
        if (chosen == null) null
        else touched?.takeIf { (row, col) -> cells[row][col] == chosen } ?: run {
            // Nearest cell to a typed color.
            var best = 0 to 0
            var bestD = Int.MAX_VALUE
            for (row in 0 until rows) for (col in 0 until cols) {
                val c = cells[row][col]
                val dr = ((c shr 16) and 0xFF) - ((chosen shr 16) and 0xFF)
                val dg = ((c shr 8) and 0xFF) - ((chosen shr 8) and 0xFF)
                val db = (c and 0xFF) - (chosen and 0xFF)
                val d = dr * dr + dg * dg + db * db
                if (d < bestD) { bestD = d; best = row to col }
            }
            best
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom color") },
        confirmButton = {
            TextButton(
                onClick = { onPick(GoogleColors.customKey(ri!!, gi!!, bi!!)) },
                enabled = chosen != null
            ) { Text("Use") }
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
                            fun pick(p: Offset) {
                                val col = (p.x / size.width * cols).toInt().coerceIn(0, cols - 1)
                                val row = (p.y / size.height * rows).toInt().coerceIn(0, rows - 1)
                                touched = row to col
                                setRgb(cells[row][col])
                            }
                            // Follows the finger while it slides, not only when it's lifted.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                pick(down.position)
                                down.consume()
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    pick(ch.position)
                                    ch.consume()
                                }
                            }
                        }
                ) {
                    val cw = size.width / cols
                    val ch = size.height / rows
                    for (row in 0 until rows) for (col in 0 until cols) {
                        drawRect(Color(cells[row][col]), topLeft = Offset(col * cw, row * ch), size = Size(cw + 0.5f, ch + 0.5f))
                    }
                    // Marker: The chosen color, with a ring in its negative color inside it.
                    if (chosen != null && marker != null) {
                        val (row, col) = marker
                        val center = Offset((col + 0.5f) * cw, (row + 0.5f) * ch)
                        val rad = minOf(cw, ch) * 0.95f
                        val negative = Color(0xFF000000.toInt() or (chosen.inv() and 0xFFFFFF))
                        drawCircle(Color(chosen), rad, center)
                        drawCircle(negative, rad, center, style = Stroke(width = 1.5.dp.toPx()))
                        drawCircle(negative, rad * 0.45f, center, style = Stroke(width = 2.dp.toPx()))
                    }
                }
                Text(
                    "Slide on the map, or type 0–255 or a hex code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("R", r) { v: String -> r = v }, Triple("G", g) { v: String -> g = v }, Triple("B", b) { v: String -> b = v })
                        .forEach { (label, value, set) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v ->
                                    set(v.filter(Char::isDigit).take(3))
                                    touched = null
                                    val rr = r.toIntOrNull()?.takeIf { it in 0..255 }
                                    val gg = g.toIntOrNull()?.takeIf { it in 0..255 }
                                    val bb = b.toIntOrNull()?.takeIf { it in 0..255 }
                                    if (rr != null && gg != null && bb != null) hex = hexOf((rr shl 16) or (gg shl 8) or bb)
                                },
                                label = { Text(label) },
                                singleLine = true,
                                isError = value.toIntOrNull()?.let { it !in 0..255 } ?: true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        val f = v.removePrefix("#").filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                        hex = f
                        if (f.length == 6) {
                            val c = f.toInt(16)
                            r = ((c shr 16) and 0xFF).toString()
                            g = ((c shr 8) and 0xFF).toString()
                            b = (c and 0xFF).toString()
                            touched = null
                        }
                    },
                    label = { Text("Hex") },
                    prefix = { Text("#") },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (chosen != null) Color(chosen) else Color.Transparent)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                    },
                    singleLine = true,
                    isError = hex.length != 6,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}
