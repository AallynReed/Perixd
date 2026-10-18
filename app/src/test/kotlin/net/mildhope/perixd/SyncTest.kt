package net.mildhope.perixd

import kotlinx.serialization.json.JsonNull
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.ThemeMode
import net.mildhope.perixd.data.withDay
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.data.withSettings
import net.mildhope.perixd.sync.Pairing
import net.mildhope.perixd.sync.Records
import net.mildhope.perixd.sync.Remote
import net.mildhope.perixd.sync.SyncKeys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class SyncTest {
    private val day = LocalDate.parse("2026-10-18")

    @Test
    fun hkdfMatchesRfc5869() {
        val ikm = ByteArray(22) { 0x0b }
        val expected = "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        val out = SyncKeys.hkdf(ikm, "", 42).joinToString("") { "%02x".format(it) }
        assertEquals(expected, out)
    }

    @Test
    fun sealedRecordsOpenOnlyWithTheSameSecretAndId() {
        val keys = SyncKeys(ByteArray(32) { it.toByte() })
        val id = keys.recordId("d:2026-10-18")
        val blob = keys.seal(id, "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), keys.open(id, blob))
        assertNotEquals(blob, keys.seal(id, "hello".toByteArray()))
        assertThrows(Exception::class.java) { keys.open(keys.recordId("d:2026-10-19"), blob) }
        assertThrows(Exception::class.java) { SyncKeys(ByteArray(32)).open(id, blob) }
        assertEquals(32, keys.spaceId.length)
        assertEquals(22, id.length)
    }

    @Test
    fun pairingLinksRoundTrip() {
        val pairing = Pairing("https://perixd.example.net", ByteArray(32) { 7 })
        assertEquals(pairing, Pairing.parse(pairing.link()))
        assertNull(Pairing.parse("https://example.net"))
        assertNull(Pairing.parse("perixd://pair?u=ftp%3A%2F%2Fx&k=AAAA"))
    }

    @Test
    fun changesAreFoundPerRecordAndThemeStaysLocal() {
        val old = Data().withDay(day, DayLog(Flow.LIGHT))
        val new = old.withDay(day, DayLog(Flow.HEAVY)).withDose(day, 5).withDay(day.minusDays(1), DayLog(note = "x"))
        assertEquals(setOf("d:2026-10-18", "p:2026-10-18", "d:2026-10-17"), Records.changedKeys(old, new))
        assertEquals(emptySet<String>(), Records.changedKeys(old, old.withSettings { copy(theme = ThemeMode.DARK) }))
        assertEquals(setOf("s"), Records.changedKeys(old, old.withSettings { copy(cycleLength = 30) }))
        assertEquals(JsonNull, Records.value(Data(), "p:2026-10-18"))
    }

    @Test
    fun newestStampWinsAndDeletionsSync() {
        val phone = Data().withDose(day, 100).copy(stamps = mapOf("p:2026-10-18" to 10))
        val tablet = Data()
        val merged = Records.merge(tablet, listOf(Remote("p:2026-10-18", Records.value(phone, "p:2026-10-18"), 10)))
        assertEquals(100L, merged.doses[day])
        val undone = Records.merge(merged, listOf(Remote("p:2026-10-18", JsonNull, 20)))
        assertNull(undone.doses[day])
        val stale = Records.merge(undone, listOf(Remote("p:2026-10-18", Records.value(phone, "p:2026-10-18"), 15)))
        assertNull(stale.doses[day])
    }

    @Test
    fun settingsMergeKeepsTheLocalTheme() {
        val local = Data().withSettings { copy(theme = ThemeMode.DARK) }
        val remote = Data().withSettings { copy(method = method.copy(type = MethodType.PILL)) }
        val merged = Records.merge(local, listOf(Remote("s", Records.value(remote, "s"), 1)))
        assertEquals(MethodType.PILL, merged.settings.method.type)
        assertEquals(ThemeMode.DARK, merged.settings.theme)
    }

    @Test
    fun tiesGoToTheRemoteOnlyWhenJoining() {
        val local = Data().withDay(day, DayLog(Flow.LIGHT))
        val remote = listOf(Remote("d:2026-10-18", Records.value(Data().withDay(day, DayLog(Flow.HEAVY)), "d:2026-10-18"), 0))
        assertEquals(Flow.LIGHT, Records.merge(local, remote).days[day]?.flow)
        assertEquals(Flow.HEAVY, Records.merge(local, remote, winTies = true).days[day]?.flow)
    }
}
