package net.mildhope.perixd

import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.MethodType
import net.mildhope.perixd.data.Store
import net.mildhope.perixd.data.Symptom
import net.mildhope.perixd.data.withDay
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.data.withMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class StoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val day = LocalDate.parse("2026-03-01")

    @Test
    fun roundTripsThroughTheFile() {
        val file = folder.root.resolve("data.json")
        val store = Store(file)
        var changes = 0
        store.onChange = { changes++ }
        store.update { it.withDay(day, DayLog(Flow.HEAVY, mapOf(Symptom.CRAMPS to 3, Symptom.HEADACHE to 1), "ugh")) }
        store.update { it.withDose(day, 1234) }
        store.update { it }
        assertEquals(2, changes)
        assertEquals(store.data.value, Store(file).data.value)
        assertEquals(3, Store(file).data.value.days.getValue(day).symptoms[Symptom.CRAMPS])
    }

    @Test
    fun unreadableFileIsKeptAsideNotOverwritten() {
        val file = folder.root.resolve("data.json")
        file.writeText("{not json")
        assertEquals(Data(), Store(file).data.value)
        assertTrue(folder.root.list()!!.any { it.startsWith("data.json.unreadable-") })
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val file = folder.root.resolve("data.json")
        file.writeText("""{"days":{"2026-03-01":{"flow":"LIGHT","mood":"x"}},"future":1}""")
        assertEquals(Flow.LIGHT, Store(file).data.value.days.getValue(day).flow)
    }

    @Test
    fun clearingADayRemovesIt() {
        val data = Data().withDay(day, DayLog(Flow.LIGHT)).withDay(day, DayLog())
        assertTrue(data.days.isEmpty())
    }

    @Test
    fun changingTheRegimenRestartsTracking() {
        val today = LocalDate.parse("2026-03-10")
        val set = Data().withMethod(LocalDate.parse("2026-03-01")) { copy(type = MethodType.PILL, start = LocalDate.parse("2026-02-20")) }
        assertEquals(LocalDate.parse("2026-03-01"), set.settings.method.trackedFrom)
        val moved = set.withMethod(today) { copy(start = LocalDate.parse("2026-02-21")) }
        assertEquals(LocalDate.parse("2026-03-01"), moved.settings.method.trackedFrom)
        val changed = moved.withMethod(today) { copy(activePills = 24, placeboPills = 4, breakDays = 0) }
        assertEquals(today, changed.settings.method.trackedFrom)
    }
}
