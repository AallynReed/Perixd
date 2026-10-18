@file:UseSerializers(LocalDateSerializer::class, LocalTimeSerializer::class)

package net.mildhope.perixd.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.LocalTime

enum class Flow { SPOTTING, LIGHT, MEDIUM, HEAVY }

enum class Symptom { CRAMPS, HEADACHE, BACK_PAIN, BLOATING, BREAST_TENDERNESS, FATIGUE, NAUSEA, ACNE, MOOD }

/** Symptom intensities run from 1 (mild) to 3 (severe); absent means none. */
@Serializable
data class DayLog(
    val flow: Flow? = null,
    val symptoms: Map<Symptom, Int> = emptyMap(),
    val note: String = "",
) {
    val isEmpty get() = flow == null && symptoms.isEmpty() && note.isBlank()
}

enum class MethodType { NONE, PILL, PATCH, RING, INJECTION, LONG_ACTING }

@Serializable
data class Method(
    val type: MethodType = MethodType.NONE,
    /** Day 1 of any pack or cycle of the method; later cycles follow on from it. */
    val start: LocalDate? = null,
    /** Doses before this day are assumed taken and never reminded. */
    val trackedFrom: LocalDate? = null,
    val activePills: Int = 21,
    val placeboPills: Int = 0,
    val breakDays: Int = 7,
    val hormoneFreeWeek: Boolean = true,
    val injectionWeeks: Int = 13,
    val hormonal: Boolean = true,
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Serializable
data class Notifications(
    val doseEnabled: Boolean = true,
    val time: LocalTime = LocalTime.of(21, 0),
    val weekendTime: LocalTime? = null,
    val persistent: Boolean = true,
    val repeatMinutes: Int = 0,
    val snoozeMinutes: Int = 15,
    val daysBefore: Int = 1,
    val newPackNotice: Boolean = true,
    val periodDaysBefore: Int = 2,
    val fertileNotice: Boolean = false,
    val dailyLog: Boolean = false,
    val dailyLogTime: LocalTime = LocalTime.of(20, 0),
    val noticeTime: LocalTime = LocalTime.of(9, 0),
    val hideOnLockScreen: Boolean = true,
)

@Serializable
data class Settings(
    val theme: ThemeMode = ThemeMode.LIGHT,
    val method: Method = Method(),
    val notifications: Notifications = Notifications(),
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lutealLength: Int = 14,
)

@Serializable
data class Data(
    val days: Map<LocalDate, DayLog> = emptyMap(),
    /** Scheduled dose day to the time it was marked done, in epoch milliseconds. */
    val doses: Map<LocalDate, Long> = emptyMap(),
    val settings: Settings = Settings(),
    /** When each sync record last changed, in epoch milliseconds; see [net.mildhope.perixd.sync.Records]. */
    val stamps: Map<String, Long> = emptyMap(),
)

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalTimeSerializer : KSerializer<LocalTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalTime = LocalTime.parse(decoder.decodeString())
}
