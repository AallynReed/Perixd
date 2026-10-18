package net.mildhope.perixd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.mildhope.perixd.R
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.data.DayLog
import net.mildhope.perixd.data.Flow
import net.mildhope.perixd.data.Symptom
import net.mildhope.perixd.data.withDay
import net.mildhope.perixd.data.withDose
import net.mildhope.perixd.domain.doseOn
import net.mildhope.perixd.store
import java.time.LocalDate

/** Flow, symptoms with their intensity, a note, and the day's dose, for any day up to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditor(data: Data, date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val res = LocalResources.current
    val log = data.days[date] ?: DayLog()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var note by rememberSaveable(date) { mutableStateOf(log.note) }
    val latestNote by rememberUpdatedState(note)
    fun update(change: (DayLog) -> DayLog) = context.store.update { it.withDay(date, change(it.days[date] ?: DayLog())) }
    DisposableEffect(date) {
        onDispose { if (latestNote != (context.store.data.value.days[date]?.note ?: "")) update { it.copy(note = latestNote.trim()) } }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(Labels.longDay(res, date), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            Text(stringResource(R.string.flow), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (flow in listOf<Flow?>(null) + Flow.entries) {
                    FilterChip(selected = log.flow == flow, onClick = { update { it.copy(flow = flow) } }, label = { Text(stringResource(Labels.flowName(flow))) })
                }
            }

            val method = data.settings.method
            val dose = method.doseOn(date)
            if (dose != null && date >= (method.trackedFrom ?: date)) {
                HorizontalDivider()
                SwitchRow(
                    Labels.position(res, method, date).orEmpty(),
                    checked = date in data.doses,
                    onChange = { done -> context.store.update { it.withDose(date, if (done) System.currentTimeMillis() else null) } },
                    subtitle = stringResource(if (dose.isPill) R.string.taken else R.string.done),
                )
            }

            HorizontalDivider()
            Text(stringResource(R.string.symptoms), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            for (symptom in Symptom.entries) {
                val level = log.symptoms[symptom] ?: 0
                SymptomRow(stringResource(Labels.symptomName(symptom)), level) { new ->
                    update { it.copy(symptoms = if (new == 0) it.symptoms - symptom else it.symptoms + (symptom to new)) }
                }
            }

            HorizontalDivider()
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.note)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            Button(
                onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.done)) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** A symptom with a three-step intensity; tapping the current step clears it. */
@Composable
private fun SymptomRow(name: String, level: Int, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Labels.intensityName(level)), style = MaterialTheme.typography.bodySmall,
                color = if (level > 0) scheme.primary else scheme.onSurfaceVariant,
            )
        }
        val labels = listOf(R.string.intensity_mild, R.string.intensity_moderate, R.string.intensity_severe)
        for (step in 1..3) {
            val label = stringResource(labels[step - 1])
            Box(
                Modifier.padding(start = 6.dp).size(40.dp)
                    .selectable(selected = level == step, role = Role.RadioButton) { onChange(if (level == step) 0 else step) }
                    .semantics { contentDescription = "$name, $label" },
                contentAlignment = Alignment.Center,
            ) {
                val filled = step <= level
                Box(
                    Modifier.size((14 + step * 5).dp).background(if (filled) scheme.primary.copy(alpha = 0.45f + step * 0.18f) else scheme.surfaceContainerHighest, CircleShape)
                        .border(1.dp, if (filled) scheme.primary else scheme.outlineVariant, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(2.dp))
    }
}
