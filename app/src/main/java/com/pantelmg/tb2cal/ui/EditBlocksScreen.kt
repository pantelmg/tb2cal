package com.pantelmg.tb2cal.ui

import androidx.compose.runtime.collectAsState

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.res.painterResource
import com.pantelmg.tb2cal.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.pantelmg.tb2cal.model.GoogleColors
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
    // Removing a TimeBlock that has goals asks first (its goals go too; Past ones stay unless previous day editing is on).
    var confirmRemove by remember { mutableStateOf<BlockType?>(null) }

    fun persist() = Repo.setBlocks(rows.toList())
    fun keyOf(b: BlockType) = b.name.trim().lowercase()
    fun remove(b: BlockType) {
        rows.removeAll { it.id == b.id }
        persist()
    }

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

    // Touch and hold ≡ to move a TimeBlock (short vibration, pulse), like the Calendar tab's day list: The row
    // follows the finger, swaps with a neighbour once it covers half of it, and the list scrolls slowly when the row
    // reaches its top or bottom. The new order is saved when let go (TimeBlocks tab, widget, Goals).
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val edgePx = with(density) { 56.dp.toPx() }
    val maxSpeedPx = with(density) { 420.dp.toPx() } // Per second, at the very edge.
    val handlePx = with(density) { 48.dp.toPx() } // The ≡ (40dp) at each row's end, plus the list's end padding.
    val pulse = remember { Pulse() }
    var dragList by remember { mutableStateOf<List<BlockType>?>(null) }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var grabY by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    var dropKey by remember { mutableStateOf<String?>(null) }
    val dropY = remember { Animatable(0f) }
    fun itemKey(b: BlockType) = "b/" + b.id
    val firstRow = 1 // The hint above the rows.

    fun moveIfNeeded() {
        val list = dragList ?: return
        val k = dragKey ?: return
        val from = list.indexOfFirst { itemKey(it) == k }
        if (from < 0) return
        val visible = listState.layoutInfo.visibleItemsInfo
        val cur = visible.firstOrNull { it.key == k } ?: return
        // The list hasn't laid out the last move yet: Decide on the next frame (never on stale positions).
        if (cur.index != from + firstRow) return
        val top = fingerY - grabY
        val bottom = top + cur.size
        val below = list.getOrNull(from + 1)?.let { n -> visible.firstOrNull { it.key == itemKey(n) } }
        val above = list.getOrNull(from - 1)?.let { p -> visible.firstOrNull { it.key == itemKey(p) } }
        val to = when {
            below != null && bottom > below.offset + below.size / 2f -> from + 1
            above != null && top < above.offset + above.size / 2f -> from - 1
            else -> return
        }
        // The list keeps its first visible item in place (or it would follow the moved row): Same frame.
        val first = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        dragList = list.toMutableList().apply { add(to, removeAt(from)) }
        if (from + firstRow == first || to + firstRow == first) listState.requestScrollToItem(first, firstOffset)
    }

    fun endDrag() {
        val k = dragKey
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
        if (k != null && info != null) {
            // Let go: The row glides from under the finger into its place (no snap).
            val from = fingerY - grabY - info.offset
            dropKey = k
            scope.launch {
                dropY.snapTo(from)
                dropY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                if (dropKey == k) dropKey = null
            }
        }
        dragList?.let { l ->
            if (l.map { it.id } != rows.map { it.id }) {
                rows.clear()
                rows.addAll(l)
                persist()
            }
        }
        dragList = null
        dragKey = null
        dragMoved = false
    }

    // While dragging, every frame: Slow scroll near an edge (same speed on any refresh rate), and moves settle.
    fun startFrameLoop() = scope.launch {
        var last = 0L
        while (dragKey != null) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
            last = now
            val info = listState.layoutInfo
            // The rows' area: Below the top padding, above the bottom one (kept free for the Add button).
            val end = (info.viewportEndOffset - info.afterContentPadding).toFloat()
            // By the dragged row's own edges (not the finger), once it's been moved.
            val size = info.visibleItemsInfo.firstOrNull { it.key == dragKey }?.size ?: 0
            val top = fingerY - grabY
            val bottom = top + size
            val depth = when {
                !dragMoved -> 0f
                top < edgePx -> -((edgePx - top) / edgePx).coerceIn(0f, 1f)
                bottom > end - edgePx -> ((bottom - (end - edgePx)) / edgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (depth != 0f && dt > 0f) {
                // A scroll step can be interrupted: Just go on.
                try {
                    listState.scrollBy(depth * maxSpeedPx * dt)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                }
            }
            // Every frame (also with the finger still): Finishes any move a fast drag left half-way.
            moveIfNeeded()
        }
    }

    // One gesture for the whole list (it keeps going when the row scrolls), started only on a row's ≡: Holding the
    // name box still selects its text, and the color dot and bin work as usual.
    // Touch positions in the rows' own measure (the list's top padding taken off), like the rows' offsets.
    fun rowY(y: Float) = y + listState.layoutInfo.viewportStartOffset
    val listDrag = Modifier.pointerInput(Unit) {
        detectDragGesturesAfterLongPress(
            onDragStart = { touch ->
                if (touch.x < size.width - handlePx) return@detectDragGesturesAfterLongPress
                val y = rowY(touch.y)
                val hit = listState.layoutInfo.visibleItemsInfo.firstOrNull { v ->
                    (v.key as? String)?.startsWith("b/") == true && y >= v.offset && y < v.offset + v.size
                } ?: return@detectDragGesturesAfterLongPress
                focus.clearFocus() // The keyboard closes, so the list doesn't change height mid-drag.
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dragList = rows.toList()
                dragKey = hit.key as String
                dragMoved = false
                dropKey = null
                fingerY = y
                grabY = y - hit.offset
                startFrameLoop()
                pulse.fire(hit.key as String)
            },
            onDrag = { change, _ ->
                if (dragKey == null) return@detectDragGesturesAfterLongPress
                change.consume()
                fingerY = rowY(change.position.y)
                dragMoved = true
                moveIfNeeded()
            },
            onDragEnd = { endDrag() },
            onDragCancel = { endDrag() }
        )
    }

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
                .padding(pad)
                .then(listDrag),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "hint") {
                Text(
                    "Tap a color dot to set its calendar color; Touch and hold ≡ to move a TimeBlock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(dragList ?: rows, key = { itemKey(it) }) { b ->
                val k = itemKey(b)
                val dragging = dragKey == k
                val dropping = dropKey == k
                PulseBox(
                    k, GoogleColors.byKey(b.colorKey)?.let { Color(it.argb) } ?: MaterialTheme.colorScheme.outline, pulse,
                    Modifier
                        // The others slide to their new places (only during a drag); The dragged one follows the finger.
                        .animateItem(
                            fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (dragging || dropping || (dragKey == null && dropKey == null)) null
                            else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                        )
                        .zIndex(if (dragging || dropping) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                dragging -> {
                                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == k }
                                    if (info == null) 0f else fingerY - grabY - info.offset
                                }
                                dropping -> dropY.value
                                else -> 0f
                            }
                        }
                ) {
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
                                // Goals belong to the name: Only if no other TimeBlock in the list has the same name.
                                val key = keyOf(b)
                                val shared = rows.any { it.id != b.id && keyOf(it) == key }
                                if (!shared && key.isNotEmpty() && com.pantelmg.tb2cal.model.Goal.hasGoals(Repo.goals.value, key)) confirmRemove = b
                                else remove(b)
                            },
                            enabled = rows.size > 1
                        ) { Icon(Icons.Default.Delete, contentDescription = "Remove ${b.name}") }
                        // ≡: Touch and hold to move (a short tap pulses, as a hint).
                        Box(
                            Modifier
                                .size(40.dp)
                                .clickable { pulse.fire(k) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_s_drag), contentDescription = "Move ${b.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    confirmRemove?.let { b ->
        val allowPast = Repo.settings.collectAsState().value.allowPast
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${b.name.trim()}?") },
            text = {
                Text(
                    if (allowPast) "It has goals: All of them will be deleted too (Allow previous day editing is ON)."
                    else "It has goals: Those from the current periods on will be deleted too; Goals of past periods " +
                        "stay as a record (Allow previous day editing is OFF)."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    Repo.setGoals(
                        com.pantelmg.tb2cal.model.Goal.deleteName(
                            Repo.goals.value, keyOf(b), allowPast, com.pantelmg.tb2cal.model.Plan.today()
                        ) { currentPeriodStart(it) }
                    )
                    remove(b)
                    confirmRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } }
        )
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
