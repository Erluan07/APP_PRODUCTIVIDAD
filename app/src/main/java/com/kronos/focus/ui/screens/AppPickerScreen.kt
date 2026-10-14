package com.kronos.focus.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kronos.focus.data.AllowedAppsStore
import com.kronos.focus.domain.rules.AppRulesConfig
import com.kronos.focus.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppItem(val label: String, val pkg: String)

private fun installedApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
        .mapNotNull { it.activityInfo?.packageName }.toSet()
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg in AppRulesConfig.CORE_ALLOWED || pkg in launchers || pkg in AppRulesConfig.ALWAYS_BLOCKED_IN_BUNKER ||
                AppRulesConfig.BLOCKED_PREFIXES.any { pkg.startsWith(it) } || pkg.contains(".settings")) null
            else AppItem(ri.loadLabel(pm).toString(), pkg)
        }
        .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
}

/**
 * Primera vez que abres Kronos: eliges qué apps quedan habilitadas durante el búnker. Al confirmar, la lista
 * se bloquea y ya no se puede cambiar. El Teléfono siempre está permitido (emergencias).
 */
@Composable
fun AppPickerScreen(ctx: Context, onDone: () -> Unit) {
    val apps by produceState<List<AppItem>?>(null) { value = withContext(Dispatchers.Default) { installedApps(ctx) } }
    val selected = remember { mutableStateListOf<String>() }
    var preselected by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }

    LaunchedEffect(apps) {
        val list = apps ?: return@LaunchedEffect
        if (!preselected) {
            preselected = true
            list.filter { AppRulesConfig.isUserApp(it.pkg, null) }.forEach { selected += it.pkg }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("¿Qué apps quieres poder usar en el Modo Búnker?", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            MutedText("Elige solo las que de verdad necesitas. Esta elección se hace una vez y después no se puede cambiar. El Teléfono siempre queda permitido.")
            OutlinedTextField(
                query, { query = it }, singleLine = true, label = { Text("Buscar app") },
                shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth(),
            )
            val list = apps
            if (list == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val shown = remember(list, query) { list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) } }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(shown, key = { it.pkg }) { app ->
                        val on = app.pkg in selected
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                .clickable { if (on) selected.remove(app.pkg) else selected.add(app.pkg) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) selected.remove(app.pkg) else selected.add(app.pkg) })
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                CtaButton("Confirmar (${selected.size} apps)", { confirm = true }, enabled = apps != null)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Confirmar estas ${selected.size} apps?") },
            text = { Text("Después no podrás agregar ni quitar apps. Solo se puede volver a elegir borrando los datos de Kronos o reinstalándola.") },
            confirmButton = { TextAction("Confirmar", { AllowedAppsStore.get(ctx).lock(selected.toSet()); confirm = false; onDone() }) },
            dismissButton = { TextAction("Revisar", { confirm = false }) },
        )
    }
}
