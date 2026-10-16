package com.kronos.focus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import com.kronos.focus.data.AllowedAppsStore
import com.kronos.focus.data.BunkerStateStore
import com.kronos.focus.ui.components.FloatingDock
import com.kronos.focus.ui.theme.UiDesign
import com.kronos.focus.services.AccessibilityGuard
import com.kronos.focus.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Acciones que dependen de la Activity (permisos, selector de archivos, cámara). */
class AppActions(
    val importFile: () -> Unit,
    val paste: () -> Unit,
    val startSleep: () -> Unit,
    val openProof: (String, String) -> Unit,
    val requestCamera: () -> Unit,
    val requestCalendar: () -> Unit,
    val requestSteps: () -> Unit,
    val importModel: () -> Unit,
)

/** Las pantallas la llaman tras cambiar el estado (iniciar búnker, dormir…) para repintar al instante. */
val LocalRefresh = staticCompositionLocalOf<() -> Unit> { {} }

private data class Tab(val label: String, val icon: ImageVector)

@Composable
fun AppRoot(actions: AppActions) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var a11y by remember { mutableStateOf(AccessibilityGuard.isEnabled(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh = remember { { now = System.currentTimeMillis() } }
    val store = remember { BunkerStateStore.get(ctx) }
    // La cuenta atrás se ve solo en "Hoy" con algo activo. Este valor es la CLAVE del bucle: cuando cambia
    // (empieza el búnker, cambias de pestaña…) el bucle se reinicia al instante en vez de esperar su pausa de 5 s.
    // (Antes, un bucle dormido en una pausa larga dejaba el cronómetro congelado hasta cambiar de pestaña.)
    // OJO: `remember` siempre se llama (nunca dentro de un && / ||); si no, al cambiar de pestaña Compose desalinea sus posiciones y falla.
    val bunkerActive = remember(now) { store.isActive(now) }
    val countdown = tab == 0 && (bunkerActive || com.kronos.focus.services.AwakeStatus.active)
    LaunchedEffect(lifecycle, countdown) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                // Lecturas de Settings y Device Owner fuera del hilo principal para no frenar el dibujado.
                val enabled = withContext(Dispatchers.Default) {
                    DeviceOwnerHardening.sync(ctx, store.isActive(now))
                    AccessibilityGuard.isEnabled(ctx)
                }
                if (enabled != a11y) a11y = enabled
                delay(if (countdown) 1000L else 5_000L)
            }
        }
    }
    var appsChosen by remember { mutableStateOf(AllowedAppsStore.get(ctx).isLocked()) }
    if (!appsChosen) {
        AppPickerScreen(ctx, onDone = { appsChosen = true })
        return
    }
    val midnight = UiDesign.current == UiDesign.MIDNIGHT
    val tabs = listOf(
        Tab("Hoy", Icons.Filled.Home), Tab("Alarmas", Icons.Filled.Notifications),
        Tab("Tareas", Icons.Filled.CheckCircle), Tab("Más", Icons.Filled.Settings),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (midnight) {
                FloatingDock(tabs.map { it.label }, tabs.map { it.icon }, tab) { tab = it; now = System.currentTimeMillis() }
            } else NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i; now = System.currentTimeMillis() },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).statusBarsPadding()) {
            CompositionLocalProvider(LocalRefresh provides refresh) {
                when (tab) {
                    0 -> TodayScreen(ctx, now, a11y, actions)
                    1 -> AlarmsScreen(ctx, now, actions)
                    2 -> TasksScreen(ctx, now, actions)
                    else -> MoreScreen(ctx, now, a11y, actions)
                }
            }
        }
    }
}
