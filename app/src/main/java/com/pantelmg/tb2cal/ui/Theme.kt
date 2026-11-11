package com.pantelmg.tb2cal.ui

import android.os.Build
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Calendar color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GoogleColors.all.chunked(4).forEach { row ->
                    Row {
                        row.forEach { gc ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(64.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onPick(gc.key) }
                                    .padding(vertical = 6.dp)
                            ) {
                                ColorDot(gc.key, 34.dp, selected = current == gc.key)
                                Text(
                                    gc.name, style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { onPick(null) }) {
                    ColorDot(null, 18.dp, selected = current == null)
                    Text("  Calendar default")
                }
            }
        }
    )
}
