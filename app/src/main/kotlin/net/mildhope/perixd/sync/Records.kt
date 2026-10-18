package net.mildhope.perixd.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.long
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Method
import net.mildhope.perixd.data.Notifications
import net.mildhope.perixd.data.Settings
import net.mildhope.perixd.data.Store
import java.time.LocalDate

/** Everything but the theme, which stays per device. */
@Serializable
data class SharedSettings(
    val method: Method,
    val notifications: Notifications,
    val cycleLength: Int,
    val periodLength: Int,
    val lutealLength: Int,
)

data class Remote(val key: String, val value: JsonElement, val t: Long)

/**
 * Data split into sync records: "d:<date>" for a day's log, "p:<date>" for a dose and "s" for shared settings.
 * A missing day or dose is a JSON null record, so deletions sync too; the newest stamp wins.
 */
object Records {
    private const val SETTINGS = "s"
    private val json = Store.json

    fun keys(data: Data): Set<String> =
        data.days.keys.map { "d:$it" }.toSet() + data.doses.keys.map { "p:$it" } + SETTINGS

    fun value(data: Data, key: String): JsonElement = when {
        key == SETTINGS -> json.encodeToJsonElement(data.settings.shared())
        key.startsWith("d:") -> data.days[LocalDate.parse(key.drop(2))]?.let { json.encodeToJsonElement(it) } ?: JsonNull
        key.startsWith("p:") -> data.doses[LocalDate.parse(key.drop(2))]?.let { JsonPrimitive(it) } ?: JsonNull
        else -> JsonNull
    }

    fun changedKeys(old: Data, new: Data): Set<String> =
        (keys(old) + keys(new)).filterTo(mutableSetOf()) { value(old, it) != value(new, it) }

    /** Applies remote records that are newer than the local ones; [winTies] lets the remote win equal stamps. */
    fun merge(data: Data, remotes: List<Remote>, winTies: Boolean = false): Data {
        var out = data
        for (r in remotes) {
            val local = out.stamps[r.key] ?: 0
            if (r.t < local || (r.t == local && !winTies)) continue
            out = apply(out, r.key, r.value).let { it.copy(stamps = it.stamps + (r.key to r.t)) }
        }
        return out
    }

    private fun apply(data: Data, key: String, value: JsonElement): Data = runCatching {
        when {
            key == SETTINGS -> data.copy(settings = data.settings.withShared(json.decodeFromJsonElement(value)))
            key.startsWith("d:") -> {
                val date = LocalDate.parse(key.drop(2))
                if (value is JsonNull) data.copy(days = data.days - date)
                else data.copy(days = data.days + (date to json.decodeFromJsonElement<DayLog>(value)))
            }
            key.startsWith("p:") -> {
                val date = LocalDate.parse(key.drop(2))
                if (value is JsonNull) data.copy(doses = data.doses - date)
                else data.copy(doses = data.doses + (date to (value as JsonPrimitive).long))
            }
            else -> data
        }
    }.getOrDefault(data)

    private fun Settings.shared() = SharedSettings(method, notifications, cycleLength, periodLength, lutealLength)

    private fun Settings.withShared(s: SharedSettings) =
        copy(method = s.method, notifications = s.notifications, cycleLength = s.cycleLength, periodLength = s.periodLength, lutealLength = s.lutealLength)
}
