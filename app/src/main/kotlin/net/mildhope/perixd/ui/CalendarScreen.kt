package net.mildhope.perixd.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.domain.doseMoment
import net.mildhope.perixd.domain.doseOn
import net.mildhope.perixd.domain.forecast
import net.mildhope.perixd.domain.isHormonal
import net.mildhope.perixd.domain.regimen
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

@Composable
fun CalendarScreen(data: Data, today: LocalDate, padding: PaddingValues, onEditDay: (LocalDate) -> Unit) {
    val res = LocalResources.current
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today)) }
    val forecast = remember(data.days, data.settings, today) { forecast(data.days, data.settings, today) }
    val showFertility = !data.settings.method.isHormonal
    val firstDay = WeekFields.of(Labels.locale(res)).firstDayOfWeek
    var drag by remember { mutableStateOf(0f) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp, start = 12.dp, end = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_month)) }
            Text(
                Labels.month(res, month.atDay(1)).replaceFirstChar { it.titlecase(Labels.locale(res)) },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_month)) }
        }
        if (month != YearMonth.from(today)) {
            TextButton(onClick = { month = YearMonth.from(today) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.back_to_today)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            for (i in 0 until 7) {
                Text(
                    Labels.weekday(res, firstDay.plus(i.toLong())),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value).mod(7)
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        Column(
            Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag > 120) month = month.minusMonths(1) else if (drag < -120) month = month.plusMonths(1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount -> drag += amount }
            },
        ) {
            for (week in cells.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (date in week) {
                        if (date == null) Spacer(Modifier.weight(1f)) else {
                            DayCell(
                                date = date, data = data, today = today,
                                predicted = forecast.bleeds.any { date in it } && date >= today,
                                fertile = showFertility && forecast.fertileWindows.any { date in it },
                                ovulation = showFertility && date in forecast.ovulations,
                                onClick = { onEditDay(date) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Legend(showFertility, data.settings.method.regimen() != null, Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp))
        Text(
            stringResource(R.string.calendar_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate, data: Data, today: LocalDate, predicted: Boolean, fertile: Boolean, ovulation: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val log = data.days[date]
    val flow = log?.flow
    val bleeding = flow != null && flow != Flow.SPOTTING
    val method = data.settings.method
    val dose = method.doseOn(date)?.takeIf { date >= (method.trackedFrom ?: date) }
    val doseColor = when {
        dose == null -> null
        date in data.doses -> scheme.tertiary
        !data.settings.notifications.doseMoment(date, ZoneId.systemDefault()).isAfter(ZonedDateTime.now()) -> scheme.error
        else -> null
    }
    val future = date > today
    Box(
        modifier.aspectRatio(0.9f).padding(2.dp).clip(CircleShape).clickable(enabled = !future, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val r = size.minDimension / 2
            when {
                bleeding -> drawCircle(scheme.primary.copy(alpha = when (flow) { Flow.LIGHT -> 0.5f; Flow.MEDIUM -> 0.78f; else -> 1f }), r)
                fertile -> drawCircle(scheme.tertiaryContainer, r)
            }
            if (predicted && !bleeding) {
                drawCircle(scheme.primaryContainer, r)
                drawCircle(scheme.primary, r - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            }
            if (ovulation) drawCircle(scheme.tertiary, r - 1.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            if (date == today) drawCircle(scheme.onSurface, r - 0.5.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
            val dots = listOfNotNull(
                scheme.primary.takeIf { flow == Flow.SPOTTING },
                scheme.secondary.takeIf { log != null && (log.symptoms.isNotEmpty() || log.note.isNotBlank()) },
                doseColor,
            )
            val dot = r * 0.11f
            dots.forEachIndexed { i, color ->
                val x = center.x + (i - (dots.size - 1) / 2f) * dot * 3f
                drawCircle(if (bleeding && color != doseColor) Color.White else color, dot, Offset(x, size.height - dot * 2.2f))
            }
        }
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
            color = when {
                bleeding -> scheme.onPrimary
                future && !predicted && !fertile -> scheme.onSurfaceVariant
                else -> scheme.onSurface
            },
        )
    }
}

@Composable
private fun Legend(fertility: Boolean, method: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendItem(stringResource(R.string.legend_period)) { drawCircle(scheme.primary) }
        LegendItem(stringResource(R.string.legend_predicted)) {
            drawCircle(scheme.primaryContainer)
            drawCircle(scheme.primary, style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
        }
        if (fertility) {
            LegendItem(stringResource(R.string.legend_fertile)) { drawCircle(scheme.tertiaryContainer) }
            LegendItem(stringResource(R.string.legend_ovulation)) { drawCircle(scheme.tertiary, style = Stroke(width = 2.dp.toPx())) }
        }
        LegendItem(stringResource(R.string.legend_spotting)) { drawCircle(scheme.primary, size.minDimension / 5) }
        LegendItem(stringResource(R.string.legend_symptoms)) { drawCircle(scheme.secondary, size.minDimension / 5) }
        if (method) {
            LegendItem(stringResource(R.string.legend_dose_done)) { drawCircle(scheme.tertiary, size.minDimension / 5) }
            LegendItem(stringResource(R.string.legend_dose_missed)) { drawCircle(scheme.error, size.minDimension / 5) }
        }
    }
}

@Composable
private fun LegendItem(label: String, draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(16.dp), onDraw = draw)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
