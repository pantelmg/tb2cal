package com.pantelmg.tb2cal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pantelmg.tb2cal.R
import kotlinx.coroutines.launch

/** One tutorial page: Its icon (null: The app's symbol), title and a few short lines. */
private class TutorialPage(val icon: (@Composable () -> Painter)?, val title: String, val lines: List<String>)

private fun tutorialPages(firstRun: Boolean) = listOf(
    TutorialPage(
        null, "Welcome to TB2Cal",
        listOf(
            "TimeBlocks to Calendar: Track the time you really spend, and keep an honest record of it.",
            "Swipe for a short tour, or skip it; You can open it again from Settings."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_w_hourglass) }, "TimeBlocks",
        listOf(
            "Pick a TimeBlock, choose Timer (counts down) or Stopwatch (counts up) and tap START.",
            "Only completed TimeBlocks are saved: In the app and in TB2Cal's own calendar on your phone.",
            "The pencil next to your TimeBlock opens Edit TimeBlocks: Names and colors; Touch and hold ≡ to reorder them."
        )
    ),
    TutorialPage(
        { rememberVectorPainter(Icons.Default.DateRange) }, "Calendar",
        listOf(
            "Every completed TimeBlock, month by month; Tap a day to see its list.",
            "TB Total merges a day's same-name TimeBlocks; The eye hides the calendar for a bigger list.",
            "Import from your Gmail (Settings) shows your Google events too, read-only."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_w_list_both) }, "Tasks and Events",
        listOf(
            "+Task/Event in the Calendar tab: Tasks to tick (No date, a date or repeating) and Events (all day or timed).",
            "Timed ones remind you at their start, or earlier (Settings → Remind me before).",
            "Touch and hold any entry of a day's list to move it; The order shows on the widgets too."
        )
    ),
    TutorialPage(
        { rememberVectorPainter(Icons.AutoMirrored.Filled.List) }, "Statistics",
        listOf(
            "Daily, Weekly, Monthly and Yearly totals per TimeBlock, as a List, a Graph or a Pie.",
            "Switch to Tasks/Events for completed counts; Export PDF saves any period."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_s_goal) }, "Goals",
        listOf(
            "Set a time goal per TimeBlock for a day, week, month or year, and watch its bar fill.",
            "Apply onward repeats a goal in every later period; The Active goals overview lists them all."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_s_widgets) }, "Widgets",
        listOf(
            "Calendar, TimeBlock manager and Task/Event manager: Add them from your home screen's widget picker.",
            "Start, pause and complete TimeBlocks, or tick Tasks, without opening the app."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_s_lock) }, "An honest past",
        listOf(
            "Allow previous day editing is OFF by default: Past days stay exactly as they were.",
            "Turn it ON in Settings to change them; Every late change is labelled, like Added in retrospect or Ticked later."
        )
    ),
    TutorialPage(
        { painterResource(R.drawable.ic_s_shield) }, "Your data, your phone",
        listOf(
            "No account and no internet access: Everything stays on your phone.",
            "Data management and Export/Import preferences (Settings) make backups, also for a new phone.",
            "Background reliability (Settings) helps your phone keep TimeBlocks running."
        ) + if (firstRun) listOf("Next, TB2Cal asks for notifications and calendar access.") else emptyList()
    ),
)

/**
 * The tutorial: Swipeable pages with Skip, dots and Next. Shown once on a fresh install (before the permissions are
 * asked: [firstRun]), and any time from Settings → Tutorial.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(firstRun: Boolean, onDone: () -> Unit) {
    val pages = tutorialPages(firstRun)
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    // Back: The previous page (on the first one, as usual: Settings, or leaving the app on a fresh install).
    BackHandler(enabled = pager.currentPage > 0) { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Skip, top right (kept in place, invisible on the last page).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone, enabled = !last, modifier = Modifier.alpha(if (last) 0f else 1f)) { Text("Skip") }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                val p = pages[i]
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier
                            .widthIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (p.icon == null) {
                            AppSymbol(112.dp)
                        } else {
                            Box(
                                Modifier
                                    .size(112.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(p.icon.invoke(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(56.dp))
                            }
                        }
                        Spacer(Modifier.height(28.dp))
                        Text(
                            p.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        p.lines.forEach { line ->
                            Text(
                                line, style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                }
            }
            // Back · dots · Next (Get started / Done on the last page).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (pager.currentPage > 0) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Back") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(if (i == pager.currentPage) 9.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Button(onClick = {
                        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }) { Text(if (!last) "Next" else if (firstRun) "Get started" else "Done") }
                }
            }
        }
    }
}
