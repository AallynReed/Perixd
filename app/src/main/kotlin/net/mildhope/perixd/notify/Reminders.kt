package net.mildhope.perixd.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import net.mildhope.perixd.MainActivity
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.domain.Alert
import net.mildhope.perixd.domain.BleedSoon
import net.mildhope.perixd.domain.DoseAction
import net.mildhope.perixd.domain.DoseSoon
import net.mildhope.perixd.domain.DueDose
import net.mildhope.perixd.domain.FertileSoon
import net.mildhope.perixd.domain.LogReminder
import net.mildhope.perixd.domain.Runtime
import net.mildhope.perixd.domain.daysBetween
import net.mildhope.perixd.domain.doseMoment
import net.mildhope.perixd.domain.isHormonal
import net.mildhope.perixd.domain.plan
import net.mildhope.perixd.store
import net.mildhope.perixd.ui.Labels
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

object Reminders {
    const val CHANNEL_DOSE = "dose"
    const val CHANNEL_CYCLE = "cycle"
    const val CHANNEL_LOG = "log"
    private const val ID_DOSE = 1
    private const val ID_DOSE_SOON = 2
    private const val ID_BLEED = 3
    private const val ID_FERTILE = 4
    private const val ID_LOG = 5

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_DOSE, context.getString(R.string.channel_dose), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_CYCLE, context.getString(R.string.channel_cycle), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_LOG, context.getString(R.string.channel_log), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    @Synchronized
    fun sync(context: Context, now: ZonedDateTime = ZonedDateTime.now()) {
        val data = context.store.data.value
        val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
        val runtime = Runtime(
            lastCheck = prefs.getLong("lastCheck", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            snoozedUntil = prefs.getLong("snoozedUntil", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
            snoozedDay = prefs.getLong("snoozedDay", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay),
        )
        val plan = plan(data, runtime, now)
        prefs.edit {
            putLong("lastCheck", plan.runtime.lastCheck!!.toEpochMilli())
            putLong("snoozedUntil", plan.runtime.snoozedUntil?.toEpochMilli() ?: 0)
            putLong("snoozedDay", plan.runtime.snoozedDay?.toEpochDay() ?: Long.MIN_VALUE)
        }
        val manager = NotificationManagerCompat.from(context)
        when (plan.doseAction) {
            DoseAction.POST_ALERT -> post(context, ID_DOSE, doseNotification(context, data, plan.dose!!, now, alert = true))
            DoseAction.POST_SILENT -> post(context, ID_DOSE, doseNotification(context, data, plan.dose!!, now, alert = false))
            DoseAction.CANCEL -> manager.cancel(ID_DOSE)
            DoseAction.KEEP -> Unit
        }
        for (notice in plan.notices) noticeNotification(context, data, notice, now)?.let { (id, n) -> post(context, id, n) }
        schedule(context, plan.nextWake)
    }

    fun markDone(context: Context, day: LocalDate) {
        context.store.update { it.withDose(day, System.currentTimeMillis()) }
        sync(context)
    }

    fun snooze(context: Context, day: LocalDate) {
        val minutes = context.store.data.value.settings.notifications.snoozeMinutes.coerceAtLeast(1)
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit {
            putLong("snoozedUntil", System.currentTimeMillis() + minutes * 60_000L)
            putLong("snoozedDay", day.toEpochDay())
        }
        sync(context)
    }

    fun canNotify(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun canScheduleExact(context: Context) =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canNotify(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun doseNotification(context: Context, data: Data, due: DueDose, now: ZonedDateTime, alert: Boolean): android.app.Notification {
        val n = data.settings.notifications
        val method = data.settings.method
        val res = context.resources
        val today = now.toLocalDate()
        val title = Labels.doseAction(res, method, due.day, due.dose)
        val text = if (due.day < today) res.getString(R.string.dose_overdue, Labels.day(res, due.day)) else res.getString(Labels.doseHint(due.dose))
        return NotificationCompat.Builder(context, CHANNEL_DOSE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.icon_background))
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(n.doseMoment(due.day, now.zone).toInstant().toEpochMilli())
            .setShowWhen(true)
            .setOngoing(n.persistent)
            .setAutoCancel(false)
            .setSilent(!alert)
            .setContentIntent(openApp(context))
            .apply { if (n.persistent) setDeleteIntent(receiver(context, ReminderReceiver.ACTION_DISMISSED, due.day)) }
            .addAction(0, res.getString(Labels.doneAction(due.dose)), receiver(context, ReminderReceiver.ACTION_DONE, due.day))
            .apply {
                if (n.snoozeMinutes > 0) addAction(0, res.getString(R.string.snooze_action, n.snoozeMinutes), receiver(context, ReminderReceiver.ACTION_SNOOZE, due.day))
            }
            .private(context, n.hideOnLockScreen)
            .build()
    }

    private fun noticeNotification(context: Context, data: Data, alert: Alert, now: ZonedDateTime): Pair<Int, android.app.Notification>? {
        val res = context.resources
        val method = data.settings.method
        val today = now.toLocalDate()
        val (id, channel, title, text) = when (alert) {
            is DoseSoon -> {
                val inDays = daysBetween(today, alert.day)
                val title = if (alert.newPack) res.getString(R.string.new_pack_soon)
                else res.getQuantityString(R.plurals.dose_soon, inDays, res.getString(Labels.doseNoun(alert.dose)), inDays)
                Notice(ID_DOSE_SOON, CHANNEL_CYCLE, title, res.getString(R.string.on_day_at, Labels.day(res, alert.day), Labels.time(context, data.settings.notifications.doseMoment(alert.day, now.zone).toLocalTime())))
            }
            is BleedSoon -> {
                val inDays = daysBetween(today, alert.start).coerceAtLeast(0)
                val plural = if (method.isHormonal) R.plurals.bleed_soon else R.plurals.period_soon
                Notice(ID_BLEED, CHANNEL_CYCLE, res.getQuantityString(plural, inDays, inDays), res.getString(R.string.around_day, Labels.day(res, alert.start)))
            }
            is FertileSoon -> Notice(ID_FERTILE, CHANNEL_CYCLE, res.getString(R.string.fertile_today), res.getString(R.string.estimate_only))
            is LogReminder -> Notice(ID_LOG, CHANNEL_LOG, res.getString(R.string.log_reminder_title), res.getString(R.string.log_reminder_text))
            else -> return null
        }
        return id to NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.icon_background))
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .private(context, data.settings.notifications.hideOnLockScreen)
            .build()
    }

    private data class Notice(val id: Int, val channel: String, val title: String, val text: String)

    private fun NotificationCompat.Builder.private(context: Context, hide: Boolean): NotificationCompat.Builder {
        if (!hide) return setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(
            NotificationCompat.Builder(context, CHANNEL_DOSE)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(context.getColor(R.color.icon_background))
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.public_text))
                .build()
        )
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun receiver(context: Context, action: String, day: LocalDate? = null): PendingIntent = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, ReminderReceiver::class.java).setAction(action).apply { day?.let { putExtra(ReminderReceiver.EXTRA_DAY, it.toEpochDay()) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun schedule(context: Context, at: ZonedDateTime?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = receiver(context, ReminderReceiver.ACTION_WAKE)
        if (at == null) return alarms.cancel(intent)
        val millis = at.toInstant().toEpochMilli()
        if (canScheduleExact(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val day = intent.getLongExtra(EXTRA_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        when (intent.action) {
            ACTION_DONE -> day?.let { Reminders.markDone(context, it) }
            ACTION_SNOOZE -> day?.let { Reminders.snooze(context, it) }
            else -> Reminders.sync(context)
        }
    }

    companion object {
        const val ACTION_WAKE = "net.mildhope.perixd.WAKE"
        const val ACTION_DONE = "net.mildhope.perixd.DONE"
        const val ACTION_SNOOZE = "net.mildhope.perixd.SNOOZE"
        const val ACTION_DISMISSED = "net.mildhope.perixd.DISMISSED"
        const val EXTRA_DAY = "day"
    }
}

/** Boot, app updates, clock, time zone, language and exact-alarm permission changes all re-plan the reminders. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCALE_CHANGED) Reminders.createChannels(context)
        Reminders.sync(context)
    }
}
