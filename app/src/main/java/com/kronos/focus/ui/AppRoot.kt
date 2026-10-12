package com.kronos.focus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.kronos.focus.admin.DeviceOwnerHardening
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.ui.screens.*
import kotlinx.coroutines.delay

/** Acciones que dependen de la Activity (permisos, selector de archivos, cámara). */
class AppActions(
    val importFile: () -> Unit,
    val paste: () -> Unit,
    val startSleep: () -> Unit,
    val openProof: (String, String) -> Unit,
    val requestCamera: () -> Unit,
)

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(actions: AppActions) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var a11y by remember { mutableStateOf(AccessibilityGuard.isEnabled(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                a11y = AccessibilityGuard.isEnabled(ctx)
                DeviceOwnerHardening.sync(ctx, BunkerStateStore.get(ctx).isActive(now))
                delay(1000)
            }
        }
    }
    val tabs = listOf(
        Tab("Hoy", Icons.Filled.Home), Tab("Dormir", Icons.Filled.Notifications), Tab("Tareas", Icons.Filled.CheckCircle),
        Tab("Progreso", Icons.Filled.DateRange), Tab("Sistema", Icons.Filled.Settings),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).statusBarsPadding()) {
            when (tab) {
                0 -> TodayScreen(ctx, now, a11y, actions)
                1 -> SleepScreen(ctx, now, actions)
                2 -> TasksScreen(ctx, now, actions)
                3 -> ProgressScreen(ctx, now)
                else -> SystemScreen(ctx, now, a11y, actions)
            }
        }
    }
}
