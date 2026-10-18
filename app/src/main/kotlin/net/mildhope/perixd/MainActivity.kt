package net.mildhope.perixd

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import net.mildhope.perixd.data.Data
import net.mildhope.perixd.domain.regimen
import net.mildhope.perixd.notify.Reminders
import net.mildhope.perixd.sync.Sync
import net.mildhope.perixd.ui.SyncDialogs
import net.mildhope.perixd.ui.CalendarScreen
import net.mildhope.perixd.ui.DayEditor
import net.mildhope.perixd.ui.InsightsScreen
import net.mildhope.perixd.ui.PerixdIcons
import net.mildhope.perixd.ui.PerixdTheme
import net.mildhope.perixd.ui.SettingsScreen
import net.mildhope.perixd.ui.Tab
import net.mildhope.perixd.ui.TodayScreen
import net.mildhope.perixd.ui.isDark
import java.time.LocalDate
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    private val pairLinks = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) takePairLink(intent)
        setContent {
            val data by store.data.collectAsState()
            val dark = isDark(data.settings.theme)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            val pairLink by pairLinks.collectAsState()
            PerixdTheme(dark) {
                PerixdRoot(data)
                pairLink?.let { SyncDialogs("sync-join", null, { pairLinks.value = null }, joinLink = it) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takePairLink(intent)
    }

    private fun takePairLink(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("perixd://pair") }?.let { pairLinks.value = it }
    }

    override fun onResume() {
        super.onResume()
        Reminders.sync(this)
        Sync.request(this)
    }
}

@Composable
private fun PerixdRoot(data: Data) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var editing by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    val today = now.toLocalDate()
    var canNotify by remember { mutableStateOf(Reminders.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = Reminders.canNotify(context) }
    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var asked by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        now = ZonedDateTime.now()
        canNotify = Reminders.canNotify(context)
    }
    LaunchedEffect(now) {
        delay(60_000L - now.second * 1_000L - now.nano / 1_000_000 + 50)
        now = ZonedDateTime.now()
    }
    val wantsNotifications = data.settings.method.regimen() != null && data.settings.notifications.doseEnabled
    LaunchedEffect(wantsNotifications) {
        if (wantsNotifications && !canNotify && !asked) {
            asked = true
            askNotifications()
        }
    }
    BackHandler(enabled = tab != Tab.TODAY) { tab = Tab.TODAY }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.TODAY -> Icons.Filled.Home
                                    Tab.CALENDAR -> Icons.Filled.DateRange
                                    Tab.INSIGHTS -> PerixdIcons.Chart
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                null,
                            )
                        },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { insets ->
        val padding = PaddingValues(bottom = insets.calculateBottomPadding())
        Box(Modifier.fillMaxSize().padding(top = insets.calculateTopPadding()).clipToBounds(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp)) {
                when (tab) {
                Tab.TODAY -> TodayScreen(data, now, padding, canNotify, { editing = it }, { tab = it }, askNotifications)
                Tab.CALENDAR -> CalendarScreen(data, today, padding) { editing = it }
                Tab.INSIGHTS -> InsightsScreen(data, today, padding)
                Tab.SETTINGS -> SettingsScreen(data, today, padding, canNotify, askNotifications)
                }
            }
        }
    }
    editing?.let { DayEditor(data, it) { editing = null } }
}
