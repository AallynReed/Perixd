package net.mildhope.perixd.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.Notifications
import net.mildhope.perixd.domain.Dose
import net.mildhope.perixd.domain.cycleStart
import net.mildhope.perixd.domain.PillState
import net.mildhope.perixd.domain.doseMoment
import net.mildhope.perixd.domain.pillState
import net.mildhope.perixd.domain.regimen
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The pack as it should look right now: popped pills are taken, ghosted ones are past their time but unmarked, today's pill pulses. */
@Composable
fun PillPack(method: Method, doses: Map<LocalDate, Long>, notifications: Notifications, now: ZonedDateTime, onToggle: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val today = now.toLocalDate()
    val regimen = method.regimen() ?: return
    val start = method.start ?: return
    val packStart = if (today < start) start else method.cycleStart(today)!!
    val trackedFrom = method.trackedFrom ?: start
    val res = LocalResources.current
    val colors = LocalPackColors.current
    val pills = (0 until regimen.length).filter { regimen.dose(it) != null }
    val breaks = (0 until regimen.length).filter { regimen.dose(it) == null }

    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(colors.foilTop, colors.foilBottom)))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                Text(
                    packStart.plusDays(c.toLong()).dayOfWeek.let { Labels.weekday(res, it) },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        for (row in pills.chunked(7)) {
            Row(Modifier.fillMaxWidth()) {
                for (index in row) {
                    val date = packStart.plusDays(index.toLong())
                    val state = pillState(method, doses, notifications, date, now)
                    val placebo = regimen.dose(index) == Dose.PLACEBO
                    val number = if (placebo) index - method.activePills + 1 else index + 1
                    val description = buildString {
                        append(res.getString(if (placebo) R.string.pos_placebo else R.string.pos_pill, number, if (placebo) method.placeboPills else method.activePills))
                        append(", ").append(Labels.day(res, date)).append(", ")
                        append(res.getString(when (state) { PillState.TAKEN -> R.string.pill_taken; PillState.GHOST -> R.string.pill_missed; PillState.IN_PACK -> R.string.pill_in_pack }))
                    }
                    Pill(
                        state, placebo, isToday = date == today, late = state == PillState.GHOST && date < today, enabled = date <= today && date >= trackedFrom,
                        onToggle = { onToggle(date) }, description = description, modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (breaks.isNotEmpty()) {
            Text(
                stringResource(R.string.break_days), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
            for (row in breaks.chunked(7)) {
                Row(Modifier.fillMaxWidth()) {
                    for (index in row) {
                        val date = packStart.plusDays(index.toLong())
                        BreakDay(passed = date < today, isToday = date == today, modifier = Modifier.weight(1f))
                    }
                    repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun Pill(state: PillState, placebo: Boolean, isToday: Boolean, late: Boolean, enabled: Boolean, onToggle: () -> Unit, description: String, modifier: Modifier) {
    val colors = LocalPackColors.current
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val popped by animateFloatAsState(if (state == PillState.IN_PACK) 0f else 1f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "pop")
    val pulse = if (isToday && state != PillState.TAKEN) {
        rememberInfiniteTransition(label = "today").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse").value
    } else 1f
    Box(
        modifier.aspectRatio(1f).padding(3.dp)
            .toggleable(value = state == PillState.TAKEN, enabled = enabled, role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(colors.bubble, r * 0.94f)
            drawCircle(Brush.radialGradient(listOf(colors.foilTop, colors.foilBottom), center, r), r * 0.86f)
            if (popped > 0f) {
                val alpha = popped.coerceIn(0f, 1f)
                drawCircle(Brush.radialGradient(listOf(colors.hole, colors.bubble), center, r * 0.8f), r * 0.78f, alpha = alpha)
                drawFlaps(colors.foilTop, colors.tear, r * 0.78f, alpha)
            }
            val pillScale = 1f - popped
            if (pillScale > 0.02f) {
                val pr = r * 0.66f * pillScale
                drawCircle(Color.Black.copy(alpha = 0.18f), pr, center + Offset(0f, r * 0.08f))
                val base = if (placebo) colors.placebo else colors.pill
                val shade = if (placebo) colors.placeboShade else colors.pillShade
                drawCircle(Brush.radialGradient(listOf(Color.White, base, shade), center + Offset(-pr * 0.35f, -pr * 0.4f), pr * 1.6f), pr)
                drawLine(shade.copy(alpha = 0.6f), center + Offset(-pr * 0.55f, 0f), center + Offset(pr * 0.55f, 0f), strokeWidth = r * 0.06f)
            }
            if (state == PillState.GHOST) {
                val pr = r * 0.6f
                drawCircle(if (placebo) colors.placebo else colors.pill, pr, alpha = 0.45f * popped.coerceIn(0f, 1f))
                drawCircle(
                    if (late) scheme.error else scheme.primary, pr, alpha = 0.8f,
                    style = Stroke(width = r * 0.07f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(r * 0.2f, r * 0.14f))),
                )
            }
            if (late) drawCircle(scheme.error, r * 0.16f, center + Offset(r * 0.62f, -r * 0.62f))
            if (isToday) drawCircle(scheme.primary.copy(alpha = pulse), r * 0.95f, style = Stroke(width = r * 0.13f))
        }
    }
}

/** Torn foil folded into the empty bubble. */
private fun DrawScope.drawFlaps(foil: Color, edge: Color, radius: Float, alpha: Float) {
    for (k in 0 until 5) {
        val angle = (k * 72 + 17) * PI / 180
        fun at(a: Double, rr: Float) = center + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat())
        val flap = Path().apply {
            val left = at(angle - 0.3, radius)
            val tip = at(angle + 0.1, radius * (0.6f + (k % 2) * 0.1f))
            val right = at(angle + 0.3, radius)
            moveTo(left.x, left.y)
            lineTo(tip.x, tip.y)
            lineTo(right.x, right.y)
            close()
        }
        drawPath(flap, foil, alpha = alpha * 0.9f)
        drawPath(flap, edge, alpha = alpha * 0.7f, style = Stroke(width = radius * 0.05f))
    }
}

@Composable
private fun BreakDay(passed: Boolean, isToday: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val colors = LocalPackColors.current
    Box(modifier.aspectRatio(2f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(14.dp)) {
            val r = size.minDimension / 2
            drawCircle(if (passed) colors.tear else colors.bubble, r * 0.6f)
            if (isToday) drawCircle(scheme.primary, r, style = Stroke(width = r * 0.3f))
        }
    }
}

/** Days of a patch, ring or injection cycle in rows of seven, marking change days and hormone-free days. */
@Composable
fun MethodDays(method: Method, doses: Map<LocalDate, Long>, notifications: Notifications, now: ZonedDateTime, modifier: Modifier = Modifier) {
    val today = now.toLocalDate()
    val regimen = method.regimen() ?: return
    val start = method.start ?: return
    val cycle = if (today < start) start else method.cycleStart(today)!!
    val trackedFrom = method.trackedFrom ?: start
    val res = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until 7) {
                Text(
                    cycle.plusDays(c.toLong()).dayOfWeek.let { Labels.weekday(res, it) },
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in (0 until regimen.length).chunked(7)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (index in week) {
                    val date = cycle.plusDays(index.toLong())
                    val free = regimen.hormoneFree?.contains(index) == true
                    val dose = regimen.dose(index)
                    val done = dose != null && (date in doses || date < trackedFrom)
                    val missed = dose != null && !done && !notifications.doseMoment(date, now.zone).isAfter(now)
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (free) scheme.surfaceContainerHighest else scheme.primaryContainer.copy(alpha = if (date < today) 0.55f else 1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                            color = if (free) scheme.onSurfaceVariant else scheme.onPrimaryContainer,
                        )
                        if (dose != null) {
                            Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                                val r = size.minDimension * 0.11f
                                drawCircle(if (missed) scheme.error else if (done) scheme.tertiary else scheme.primary, r, Offset(size.width - r, r))
                            }
                        }
                        if (date == today) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawRoundRect(scheme.primary, style = Stroke(width = 2.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
