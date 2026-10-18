package net.mildhope.perixd.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Notifications
import net.mildhope.perixd.data.ThemeMode
import net.mildhope.perixd.data.withMethod
import net.mildhope.perixd.data.withNotifications
import net.mildhope.perixd.data.withSettings
import net.mildhope.perixd.domain.CYCLE_RANGE
import net.mildhope.perixd.domain.MAX_PILLS
import net.mildhope.perixd.domain.dayIndex
import net.mildhope.perixd.domain.isHormonal
import net.mildhope.perixd.domain.regimen
import net.mildhope.perixd.notify.Reminders
import net.mildhope.perixd.store
import java.time.LocalDate
import java.time.LocalTime

private data class Pack(val active: Int, val placebo: Int, val breakDays: Int, val label: Int)

private val PACKS = listOf(
    Pack(21, 0, 7, R.string.pack_21_7),
    Pack(24, 0, 4, R.string.pack_24_4),
    Pack(21, 7, 0, R.string.pack_21_7p),
    Pack(24, 4, 0, R.string.pack_24_4p),
    Pack(28, 0, 0, R.string.pack_28),
)

@Composable
fun SettingsScreen(data: Data, today: LocalDate, padding: PaddingValues, canNotify: Boolean, onAllowNotifications: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val store = context.store
    val s = data.settings
    val m = s.method
    val n = s.notifications
    val regimen = m.regimen()
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    fun notifications(change: Notifications.() -> Notifications) = store.update { it.withNotifications(change) }
    fun time(t: LocalTime) = Labels.time(context, t)
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
    ) {
        item {
            Text(
                stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { SectionTitle(stringResource(R.string.section_method)) }
        item { ValueRow(stringResource(R.string.method), stringResource(Labels.methodName(m.type)), { dialog = "method" }, subtitle = stringResource(Labels.methodHint(m.type))) }
        when (m.type) {
            MethodType.PILL -> {
                item {
                    val pack = PACKS.firstOrNull { it.active == m.activePills && it.placebo == m.placeboPills && it.breakDays == m.breakDays }
                    ValueRow(stringResource(R.string.pack_type), pack?.let { stringResource(it.label) } ?: stringResource(R.string.pack_custom, m.activePills, m.placeboPills, m.breakDays), { dialog = "pack" })
                }
                item { StepperRow(stringResource(R.string.active_pills), m.activePills, 1..MAX_PILLS, { v -> store.update { it.withMethod(today) { copy(activePills = v) } } }) }
                item { StepperRow(stringResource(R.string.placebo_pills), m.placeboPills, 0..7, { v -> store.update { it.withMethod(today) { copy(placeboPills = v) } } }) }
                item { StepperRow(stringResource(R.string.break_length), m.breakDays, 0..7, { v -> store.update { it.withMethod(today) { copy(breakDays = v) } } }, subtitle = stringResource(R.string.break_length_hint)) }
            }
            MethodType.PATCH, MethodType.RING -> item {
                SwitchRow(stringResource(R.string.hormone_free_week), m.hormoneFreeWeek, { v -> store.update { it.withMethod(today) { copy(hormoneFreeWeek = v) } } }, subtitle = stringResource(R.string.hormone_free_week_hint))
            }
            MethodType.INJECTION -> item {
                StepperRow(
                    stringResource(R.string.injection_interval), m.injectionWeeks, 4..26,
                    { v -> store.update { it.withMethod(today) { copy(injectionWeeks = v) } } },
                    display = pluralStringResource(R.plurals.weeks, m.injectionWeeks, m.injectionWeeks),
                )
            }
            MethodType.LONG_ACTING -> item {
                SwitchRow(stringResource(R.string.hormonal_method), m.hormonal, { v -> store.update { it.withMethod(today) { copy(hormonal = v) } } }, subtitle = stringResource(R.string.hormonal_method_hint))
            }
            MethodType.NONE -> Unit
        }
        if (regimen != null) {
            val start = m.start
            if (start == null || today < start) {
                item {
                    ValueRow(stringResource(R.string.today_is), stringResource(R.string.not_set), {
                        store.update { it.withMethod(today) { copy(start = today) } }
                    }, subtitle = stringResource(R.string.today_is_hint))
                }
            } else {
                item {
                    val index = m.dayIndex(today)!!
                    StepperRow(
                        stringResource(R.string.today_is), index, 0 until regimen.length,
                        { v -> store.update { it.withMethod(today) { copy(start = today.minusDays(v.toLong())) } } },
                        display = (index + 1).toString(),
                        subtitle = Labels.position(res, m, today),
                    )
                }
            }
            item { ValueRow(stringResource(R.string.cycle_started), start?.let { Labels.day(res, it) } ?: stringResource(R.string.not_set), { dialog = "start" }) }
        }

        if (regimen != null) {
            item { SectionTitle(stringResource(R.string.section_reminders)) }
            item { SwitchRow(stringResource(R.string.dose_reminders), n.doseEnabled, { v -> notifications { copy(doseEnabled = v) } }) }
            item { ValueRow(stringResource(R.string.reminder_time), time(n.time), { dialog = "time" }, enabled = n.doseEnabled) }
            item {
                SwitchRow(
                    stringResource(R.string.weekend_time_switch), n.weekendTime != null,
                    { v -> notifications { copy(weekendTime = if (v) time else null) } }, enabled = n.doseEnabled,
                )
            }
            n.weekendTime?.let { weekend -> item { ValueRow(stringResource(R.string.weekend_time), time(weekend), { dialog = "weekend" }, enabled = n.doseEnabled) } }
            item {
                SwitchRow(
                    stringResource(R.string.keep_until_done), n.persistent, { v -> notifications { copy(persistent = v) } },
                    subtitle = stringResource(R.string.keep_until_done_hint), enabled = n.doseEnabled,
                )
            }
            item { ValueRow(stringResource(R.string.repeat_alert), repeatLabel(n.repeatMinutes), { dialog = "repeat" }, subtitle = stringResource(R.string.repeat_alert_hint), enabled = n.doseEnabled) }
            item { ValueRow(stringResource(R.string.snooze_button), snoozeLabel(n.snoozeMinutes), { dialog = "snooze" }, enabled = n.doseEnabled) }
            if (m.type == MethodType.PILL && regimen.dose(regimen.length - 1) == null) {
                item { SwitchRow(stringResource(R.string.new_pack_notice), n.newPackNotice, { v -> notifications { copy(newPackNotice = v) } }, subtitle = stringResource(R.string.new_pack_notice_hint)) }
            }
            if (m.type != MethodType.PILL) {
                item {
                    StepperRow(
                        stringResource(R.string.advance_notice), n.daysBefore, 0..14, { v -> notifications { copy(daysBefore = v) } },
                        display = if (n.daysBefore == 0) stringResource(R.string.off) else n.daysBefore.toString(),
                        subtitle = stringResource(R.string.advance_notice_hint),
                    )
                }
            }
        }

        item { SectionTitle(stringResource(R.string.section_cycle_notices)) }
        item { ValueRow(stringResource(if (m.isHormonal) R.string.bleed_notice else R.string.period_notice), daysBeforeLabel(n.periodDaysBefore), { dialog = "period" }) }
        if (!m.isHormonal) item { SwitchRow(stringResource(R.string.fertile_notice), n.fertileNotice, { v -> notifications { copy(fertileNotice = v) } }, subtitle = stringResource(R.string.estimate_only)) }
        item { ValueRow(stringResource(R.string.notice_time), time(n.noticeTime), { dialog = "notice" }, subtitle = stringResource(R.string.notice_time_hint)) }
        item { SwitchRow(stringResource(R.string.daily_log), n.dailyLog, { v -> notifications { copy(dailyLog = v) } }, subtitle = stringResource(R.string.daily_log_hint)) }
        if (n.dailyLog) item { ValueRow(stringResource(R.string.daily_log_time), time(n.dailyLogTime), { dialog = "log" }) }

        item { SectionTitle(stringResource(R.string.section_notification_behavior)) }
        item { SwitchRow(stringResource(R.string.hide_on_lock_screen), n.hideOnLockScreen, { v -> notifications { copy(hideOnLockScreen = v) } }, subtitle = stringResource(R.string.hide_on_lock_screen_hint)) }
        item {
            ValueRow(stringResource(R.string.sound_vibration), "", {
                open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, Reminders.CHANNEL_DOSE))
            }, subtitle = stringResource(R.string.sound_vibration_hint))
        }
        item {
            ValueRow(stringResource(R.string.all_notification_settings), "", {
                open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
        }
        if (!canNotify) item { ValueRow(stringResource(R.string.notifications_off), stringResource(R.string.allow), onAllowNotifications) }
        if (!Reminders.canScheduleExact(context) && Build.VERSION.SDK_INT >= 31) {
            item {
                ValueRow(stringResource(R.string.exact_alarms_off), stringResource(R.string.allow), {
                    open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                })
            }
        }
        item {
            Text(
                stringResource(R.string.reliability_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        item { SectionTitle(stringResource(R.string.section_cycle)) }
        item {
            StepperRow(
                stringResource(R.string.usual_cycle), s.cycleLength, CYCLE_RANGE, { v -> store.update { it.withSettings { copy(cycleLength = v) } } },
                subtitle = stringResource(R.string.usual_hint),
            )
        }
        item { StepperRow(stringResource(R.string.usual_period), s.periodLength, 1..14, { v -> store.update { it.withSettings { copy(periodLength = v) } } }) }
        if (!m.isHormonal) {
            item {
                StepperRow(
                    stringResource(R.string.luteal_phase), s.lutealLength, 9..18, { v -> store.update { it.withSettings { copy(lutealLength = v) } } },
                    subtitle = stringResource(R.string.luteal_phase_hint),
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_appearance)) }
        item { ValueRow(stringResource(R.string.theme), stringResource(themeName(s.theme)), { dialog = "theme" }) }
        if (Build.VERSION.SDK_INT >= 33) {
            item {
                ValueRow(stringResource(R.string.language), "", {
                    open(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")))
                })
            }
        }

        item { SectionTitle(stringResource(R.string.section_privacy)) }
        item {
            Text(
                stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item { ValueRow(stringResource(R.string.delete_logs), "", { dialog = "delete" }, subtitle = stringResource(R.string.delete_logs_hint)) }
        item {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            Text(
                stringResource(R.string.about, version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    val close = { dialog = null }
    when (dialog) {
        "method" -> ChoiceDialog(
            stringResource(R.string.method),
            MethodType.entries.map { Choice(it, stringResource(Labels.methodName(it)), stringResource(Labels.methodHint(it))) },
            m.type, { v -> store.update { it.withMethod(today) { copy(type = v) } } }, close,
        )
        "pack" -> ChoiceDialog(
            stringResource(R.string.pack_type),
            PACKS.map { Choice(it, stringResource(it.label)) },
            PACKS.firstOrNull { it.active == m.activePills && it.placebo == m.placeboPills && it.breakDays == m.breakDays },
            { p -> store.update { it.withMethod(today) { copy(activePills = p!!.active, placeboPills = p.placebo, breakDays = p.breakDays) } } }, close,
        )
        "start" -> DateDialog(m.start ?: today, today, { d -> store.update { it.withMethod(today) { copy(start = d) } } }, close)
        "time" -> TimeDialog(stringResource(R.string.reminder_time), n.time, { t -> notifications { copy(time = t) } }, close)
        "weekend" -> TimeDialog(stringResource(R.string.weekend_time), n.weekendTime ?: n.time, { t -> notifications { copy(weekendTime = t) } }, close)
        "notice" -> TimeDialog(stringResource(R.string.notice_time), n.noticeTime, { t -> notifications { copy(noticeTime = t) } }, close)
        "log" -> TimeDialog(stringResource(R.string.daily_log_time), n.dailyLogTime, { t -> notifications { copy(dailyLogTime = t) } }, close)
        "repeat" -> ChoiceDialog(
            stringResource(R.string.repeat_alert), listOf(0, 5, 10, 15, 30, 60, 120).map { Choice(it, repeatLabel(it)) },
            n.repeatMinutes, { v -> notifications { copy(repeatMinutes = v) } }, close,
        )
        "snooze" -> ChoiceDialog(
            stringResource(R.string.snooze_button), listOf(0, 5, 10, 15, 30, 60).map { Choice(it, snoozeLabel(it)) },
            n.snoozeMinutes, { v -> notifications { copy(snoozeMinutes = v) } }, close,
        )
        "period" -> ChoiceDialog(
            stringResource(if (m.isHormonal) R.string.bleed_notice else R.string.period_notice), listOf(0, 1, 2, 3, 5, 7).map { Choice(it, daysBeforeLabel(it)) },
            n.periodDaysBefore, { v -> notifications { copy(periodDaysBefore = v) } }, close,
        )
        "theme" -> ChoiceDialog(
            stringResource(R.string.theme), ThemeMode.entries.map { Choice(it, stringResource(themeName(it))) },
            s.theme, { v -> store.update { it.withSettings { copy(theme = v) } } }, close,
        )
        "delete" -> AlertDialog(
            onDismissRequest = close,
            title = { Text(stringResource(R.string.delete_logs)) },
            text = { Text(stringResource(R.string.delete_logs_confirm)) },
            confirmButton = {
                TextButton(onClick = { store.update { it.copy(days = emptyMap(), doses = emptyMap()) }; close() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = close) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun repeatLabel(minutes: Int) = when {
    minutes == 0 -> stringResource(R.string.off)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.every_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.every_minutes, minutes, minutes)
}

@Composable
private fun snoozeLabel(minutes: Int) =
    if (minutes == 0) stringResource(R.string.no_snooze) else pluralStringResource(R.plurals.minutes, minutes, minutes)

@Composable
private fun daysBeforeLabel(days: Int) =
    if (days == 0) stringResource(R.string.off) else pluralStringResource(R.plurals.days_before, days, days)

private fun themeName(mode: ThemeMode) = when (mode) {
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.SYSTEM -> R.string.theme_system
}
