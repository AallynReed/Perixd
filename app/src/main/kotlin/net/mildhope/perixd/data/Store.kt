package net.mildhope.perixd.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/** All app data in one JSON file in private storage; writes replace the file atomically. */
class Store(private val file: File) {
    private val state = MutableStateFlow(load())
    val data: StateFlow<Data> = state
    var onChange: (Data) -> Unit = {}

    fun update(change: (Data) -> Data) {
        val new = synchronized(this) {
            val old = state.value
            val new = change(old)
            if (new == old) return
            write(new)
            state.value = new
            new
        }
        onChange(new)
    }

    private fun load(): Data {
        if (!file.exists()) return Data()
        return try {
            json.decodeFromString<Data>(file.readText())
        } catch (e: Exception) {
            file.copyTo(File("${file.path}.unreadable-${System.currentTimeMillis()}"))
            Data()
        }
    }

    private fun write(data: Data) {
        val tmp = File("${file.path}.tmp")
        tmp.outputStream().use {
            it.write(json.encodeToString(data).toByteArray())
            it.fd.sync()
        }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

fun Data.withDay(date: LocalDate, log: DayLog) = copy(days = if (log.isEmpty) days - date else days + (date to log))

fun Data.withDose(day: LocalDate, doneAt: Long?) = copy(doses = if (doneAt == null) doses - day else doses + (day to doneAt))

fun Data.withSettings(change: Settings.() -> Settings) = copy(settings = settings.change())

fun Data.withNotifications(change: Notifications.() -> Notifications) = withSettings { copy(notifications = notifications.change()) }

/** Switching to another method or regimen starts tracking afresh from [today]. */
fun Data.withMethod(today: LocalDate, change: Method.() -> Method) = withSettings {
    val new = method.change()
    val restarted = new.type != method.type || new.activePills != method.activePills ||
        new.placeboPills != method.placeboPills || new.breakDays != method.breakDays ||
        new.hormoneFreeWeek != method.hormoneFreeWeek || new.injectionWeeks != method.injectionWeeks ||
        (method.start == null && new.start != null)
    copy(method = if (restarted) new.copy(trackedFrom = today) else new)
}
