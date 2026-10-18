package net.mildhope.perixd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.domain.adherence
import net.mildhope.perixd.domain.cycleStats
import net.mildhope.perixd.domain.flowByDay
import net.mildhope.perixd.domain.forecast
import net.mildhope.perixd.domain.isHormonal
import net.mildhope.perixd.domain.pastCycles
import net.mildhope.perixd.domain.periods
import net.mildhope.perixd.domain.regimen
import net.mildhope.perixd.domain.symptomSummary
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun InsightsScreen(data: Data, today: LocalDate, padding: PaddingValues) {
    val res = LocalResources.current
    val periods = remember(data.days) { periods(data.days) }
    val stats = remember(periods, today) { cycleStats(periods, today) }
    val forecast = remember(data.days, data.settings, today) { forecast(data.days, data.settings, today) }
    val cycles = remember(periods) { pastCycles(periods).take(12) }
    val symptoms = remember(data.days, periods, today) { symptomSummary(data.days, periods, today) }
    val flow = remember(data.days, periods) { flowByDay(data.days, periods) }
    val method = data.settings.method

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text(stringResource(R.string.insights), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold) }
        item {
            InsightCard(stringResource(R.string.your_cycle)) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(
                        stringResource(R.string.average_cycle),
                        stats.average?.let { pluralStringResource(R.plurals.days, it, it) } ?: "—",
                        Modifier.weight(1f),
                        detail = if (stats.shortest != null && stats.longest != null && stats.shortest != stats.longest) stringResource(R.string.range_days, stats.shortest, stats.longest) else null,
                    )
                    Stat(
                        stringResource(R.string.average_period),
                        stats.periodAverage?.let { pluralStringResource(R.plurals.days, it, it) } ?: "—",
                        Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth()) {
                    val variation = stats.variation
                    Stat(
                        stringResource(R.string.regularity),
                        when {
                            variation == null || stats.cycles.size < 2 -> "—"
                            variation <= 3 -> stringResource(R.string.very_regular)
                            variation <= 7 -> stringResource(R.string.regular)
                            else -> stringResource(R.string.irregular)
                        },
                        Modifier.weight(1f),
                        detail = variation?.takeIf { stats.cycles.size >= 2 }?.let { stringResource(R.string.varies_by, it) },
                    )
                    Stat(stringResource(R.string.cycles_logged), stats.cycles.size.toString(), Modifier.weight(1f), detail = stringResource(R.string.recent_six))
                }
                if (!method.isHormonal && forecast.cycleDay != null) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.cycle_day), forecast.cycleDay.toString(), Modifier.weight(1f))
                        forecast.window?.let { Stat(stringResource(R.string.next_period), Labels.range(res, it.start, it.endInclusive), Modifier.weight(1f)) }
                    }
                }
                if (stats.cycles.size < 3) Text(stringResource(R.string.more_cycles_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val a = adherence(data, today)
        if (method.regimen() != null && method.start != null && a.due > 0) {
            item {
                InsightCard(stringResource(R.string.doses_last_30)) {
                    Text(stringResource(R.string.doses_marked, a.done, a.due), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { if (a.due == 0) 0f else a.done.toFloat() / a.due }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (flow.isNotEmpty()) {
            item {
                InsightCard(stringResource(R.string.flow_by_day)) {
                    FlowChart(flow)
                }
            }
        }
        item {
            InsightCard(stringResource(R.string.symptoms_180)) {
                if (symptoms.isEmpty()) Text(stringResource(R.string.no_symptoms), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (s in symptoms) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Labels.symptomName(s.symptom)), style = MaterialTheme.typography.bodyLarge)
                            val days = pluralStringResource(R.plurals.days, s.days, s.days)
                            Text(
                                s.typicalDays?.let { stringResource(R.string.symptom_detail, days, if (it.first == it.last) it.first.toString() else "${it.first}–${it.last}") } ?: days,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(Labels.intensityName(s.averageIntensity.roundToInt().coerceIn(1, 3))), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (cycles.isNotEmpty()) {
            item {
                InsightCard(stringResource(R.string.cycle_history)) {
                    val longest = cycles.mapNotNull { it.length }.maxOrNull() ?: 1
                    for (c in cycles) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Labels.shortDay(res, c.start), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                            Box(Modifier.weight(1f).height(14.dp)) {
                                val scheme = MaterialTheme.colorScheme
                                Canvas(Modifier.fillMaxSize()) {
                                    val unit = size.width / maxOf(longest, c.periodLength)
                                    val total = (c.length ?: c.periodLength) * unit
                                    drawRoundRect(scheme.surfaceContainerHighest, size = Size(total, size.height), cornerRadius = CornerRadius(size.height / 2))
                                    drawRoundRect(scheme.primary, size = Size(c.periodLength * unit, size.height), cornerRadius = CornerRadius(size.height / 2))
                                }
                            }
                            Text(
                                c.length?.let { pluralStringResource(R.plurals.days, it, it) } ?: stringResource(R.string.current),
                                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End, modifier = Modifier.width(72.dp),
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FlowChart(levels: List<Double?>) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in levels) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize()) {
                        val h = size.height * ((level ?: 0.0) / 4.0).toFloat()
                        drawRoundRect(scheme.primary, Offset(0f, size.height - h), Size(size.width, h), CornerRadius(6.dp.toPx()))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.indices.forEach {
                Text((it + 1).toString(), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Text(stringResource(R.string.flow_scale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
