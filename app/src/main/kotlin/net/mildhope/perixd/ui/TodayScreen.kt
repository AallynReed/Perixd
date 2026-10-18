package net.mildhope.perixd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.withDay
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.domain.Forecast
import net.mildhope.perixd.domain.dayIndex
import net.mildhope.perixd.domain.daysBetween
import net.mildhope.perixd.domain.dates
import net.mildhope.perixd.domain.doseNumber
import net.mildhope.perixd.domain.doseOn
import net.mildhope.perixd.domain.dueDose
import net.mildhope.perixd.domain.forecast
import net.mildhope.perixd.domain.isHormonal
import net.mildhope.perixd.domain.periods
import net.mildhope.perixd.domain.regimen
import net.mildhope.perixd.store
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun TodayScreen(
    data: Data,
    now: ZonedDateTime,
    padding: PaddingValues,
    canNotify: Boolean,
    onEditDay: (LocalDate) -> Unit,
    onOpenTab: (Tab) -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val res = LocalResources.current
    val today = now.toLocalDate()
    val forecast = remember(data.days, data.settings, today) { forecast(data.days, data.settings, today) }
    val method = data.settings.method
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(stringResource(R.string.today), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(Labels.longDay(res, today), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!canNotify && method.regimen() != null && data.settings.notifications.doseEnabled) {
            item { NotificationsOffCard(onAllowNotifications) }
        }
        item { CycleCard(data, forecast, today, onEditDay, onOpenTab) }
        if (method.regimen() != null) item { MethodCard(data, now, onOpenTab) }
        item { LogCard(data, today, onEditDay) }
        item {
            Text(
                stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun NotificationsOffCard(onAllow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.notifications_off), Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) }
        }
    }
}

@Composable
private fun CycleCard(data: Data, f: Forecast, today: LocalDate, onEditDay: (LocalDate) -> Unit, onOpenTab: (Tab) -> Unit) {
    val res = LocalResources.current
    val method = data.settings.method
    val todayFlow = data.days[today]?.flow
    val bleedingToday = todayFlow != null && todayFlow != Flow.SPOTTING
    val current = remember(data.days) { periods(data.days) }.lastOrNull()
    val periodDay = current?.takeIf { bleedingToday }?.let { daysBetween(it.start, today) + 1 }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!method.isHormonal) {
                if (f.lastStart == null) {
                    Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onEditDay(today) }) { Text(stringResource(R.string.log_today)) }
                        OutlinedButton(onClick = { onOpenTab(Tab.CALENDAR) }) { Text(stringResource(R.string.open_calendar)) }
                    }
                    return@Column
                }
                val length = f.cycleLength ?: data.settings.cycleLength
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CycleRing(f.cycleDay!!.toFloat() / length, stringResource(R.string.day_n, f.cycleDay), stringResource(R.string.of_about, length))
                    Spacer(Modifier.width(20.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val window = f.window!!
                        when {
                            periodDay != null -> Headline(stringResource(R.string.period_day, periodDay))
                            f.lateBy > 0 -> Headline(pluralStringResource(R.plurals.period_late, f.lateBy, f.lateBy))
                            window.start == window.endInclusive && window.start == today -> Headline(stringResource(R.string.period_today))
                            window.start == window.endInclusive -> daysBetween(today, window.start).let { Headline(pluralStringResource(R.plurals.period_in, it, it)) }
                            else -> Headline(stringResource(R.string.period_in_range, daysBetween(today, window.start).coerceAtLeast(0), daysBetween(today, window.endInclusive)))
                        }
                        Text(
                            stringResource(if (periodDay != null) R.string.next_expected else R.string.expected_on, Labels.range(res, window.start, window.endInclusive)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val fertile = f.fertileWindows.firstOrNull { it.endInclusive >= today } ?: f.fertile
                val ovulation = f.ovulations.firstOrNull { it >= today.minusDays(1) } ?: f.ovulation
                if (fertile != null && ovulation != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Stat(stringResource(R.string.fertile_window), Labels.range(res, fertile.start, fertile.endInclusive), detail = if (today in fertile) stringResource(R.string.fertile_now) else null)
                        Stat(stringResource(R.string.ovulation), stringResource(R.string.approx, Labels.shortDay(res, ovulation)))
                    }
                    Text(stringResource(R.string.estimate_only), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val regimen = method.regimen()
                val start = method.start
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (regimen != null && start != null && today >= start) {
                        val index = method.dayIndex(today)!!
                        CycleRing((index + 1f) / regimen.length, stringResource(R.string.day_n, index + 1), stringResource(R.string.of_total, regimen.length))
                        Spacer(Modifier.width(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Headline(Labels.position(res, method, today)?.takeIf { start != null && today >= start } ?: stringResource(Labels.methodName(method.type)))
                        val free = regimen?.hormoneFree
                        if (regimen != null && start != null && free != null && today >= start) {
                            val index = method.dayIndex(today)!!
                            val cycle = today.minusDays(index.toLong())
                            Text(
                                if (index in free) stringResource(newCycleText(method.type), Labels.day(res, cycle.plusDays(regimen.length.toLong())))
                                else stringResource(freeStartsText(method.type, method.breakDays), Labels.day(res, cycle.plusDays(free.first.toLong()))),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        when {
                            bleedingToday -> Text(stringResource(R.string.bleeding_today), style = MaterialTheme.typography.bodyMedium)
                            f.followsMethod && f.nextStart != null -> Text(stringResource(R.string.bleed_expected, Labels.day(res, f.nextStart)), style = MaterialTheme.typography.bodyMedium)
                            f.lastStart != null -> Text(stringResource(R.string.last_bleed, Labels.day(res, f.lastStart)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                Text(
                    stringResource(if (f.followsMethod) R.string.fertility_hidden else R.string.no_bleed_prediction),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun newCycleText(type: MethodType) = when (type) {
    MethodType.PATCH -> R.string.new_patch_on
    MethodType.RING -> R.string.new_ring_on
    else -> R.string.new_pack_on
}

private fun freeStartsText(type: MethodType, breakDays: Int) = when (type) {
    MethodType.PATCH -> R.string.patch_free_starts
    MethodType.RING -> R.string.ring_free_starts
    else -> if (breakDays == 0) R.string.placebo_starts else R.string.break_starts
}

@Composable
private fun Headline(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
fun CycleRing(progress: Float, big: String, small: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val bar = MaterialTheme.colorScheme.primary
    Box(modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            drawArc(track, 0f, 360f, false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
            drawArc(bar, -90f, 360f * progress.coerceIn(0.02f, 1f), false, topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width), style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(small, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MethodCard(data: Data, now: ZonedDateTime, onOpenTab: (Tab) -> Unit) {
    val today = now.toLocalDate()
    val context = LocalContext.current
    val res = LocalResources.current
    val method = data.settings.method
    val regimen = method.regimen() ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PerixdIcons.Pill, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Labels.methodName(method.type)), style = MaterialTheme.typography.titleMedium)
            }
            val start = method.start
            if (start == null) {
                Text(stringResource(R.string.setup_method_text), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { onOpenTab(Tab.SETTINGS) }) { Text(stringResource(R.string.open_settings)) }
                return@Column
            }
            val toggle = { date: LocalDate ->
                context.store.update { if (date in it.doses) it.withDose(date, null) else it.withDose(date, System.currentTimeMillis()) }
            }
            when (method.type) {
                MethodType.PILL -> PillPack(method, data.doses, data.settings.notifications, now, toggle)
                MethodType.PATCH, MethodType.RING -> MethodDays(method, data.doses, data.settings.notifications, now)
                else -> if (today >= start) {
                    val index = method.dayIndex(today)!!
                    LinearProgressIndicator(progress = { (index + 1f) / regimen.length }, modifier = Modifier.fillMaxWidth())
                }
            }
            val due = dueDose(data, now)
            val trackedFrom = method.trackedFrom ?: start
            val todayDose = method.doseOn(today)
            val target = due?.day ?: today.takeIf { todayDose != null && today !in data.doses && today >= trackedFrom }
            when {
                target != null -> {
                    val dose = method.doseOn(target)!!
                    val label = if (dose.isPill) {
                        val n = method.doseNumber(target)!!.first
                        if (target < today) stringResource(R.string.mark_pill_late, n, Labels.day(res, target)) else stringResource(R.string.mark_pill, n)
                    } else {
                        stringResource(R.string.mark_done, stringResource(Labels.doseNoun(dose)))
                    }
                    Button(onClick = { toggle(target) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                todayDose != null && today in data.doses -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(8.dp))
                        val at = Labels.time(context, Instant.ofEpochMilli(data.doses.getValue(today)).atZone(ZoneId.systemDefault()).toLocalTime())
                        Text(stringResource(if (todayDose.isPill) R.string.taken_at else R.string.done_at, at), Modifier.weight(1f))
                        TextButton(onClick = { toggle(today) }) { Text(stringResource(R.string.undo)) }
                    }
                }
                else -> {
                    val next = dates(today.plusDays(1), today.plusDays(regimen.length.toLong())).firstOrNull { method.doseOn(it) != null }
                    if (next != null) {
                        Text(
                            stringResource(R.string.next_dose, Labels.doseAction(res, method, next, method.doseOn(next)!!), Labels.day(res, next)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogCard(data: Data, today: LocalDate, onEditDay: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val log = data.days[today] ?: DayLog()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PerixdIcons.Drop, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.how_today), style = MaterialTheme.typography.titleMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (flow in listOf(null) + Flow.entries) {
                    FilterChip(
                        selected = log.flow == flow,
                        onClick = { context.store.update { it.withDay(today, log.copy(flow = flow)) } },
                        label = { Text(stringResource(Labels.flowName(flow))) },
                    )
                }
            }
            if (log.symptoms.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((symptom, level) in log.symptoms) {
                        SuggestionChip(onClick = { onEditDay(today) }, label = { Text("${stringResource(Labels.symptomName(symptom))} · ${stringResource(Labels.intensityName(level))}") })
                    }
                }
            }
            if (log.note.isNotBlank()) Text(log.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { onEditDay(today) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (log.symptoms.isEmpty() && log.note.isBlank()) R.string.log_symptoms else R.string.edit_log))
            }
        }
    }
}
