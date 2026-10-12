package com.kronos.focus.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kronos.focus.ui.AppActions

/** "Más": tu progreso y el estado del sistema, con pestañas arriba. */
@Composable
fun MoreScreen(ctx: Context, now: Long, a11y: Boolean, actions: AppActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Progreso", style = MaterialTheme.typography.labelLarge) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Sistema", style = MaterialTheme.typography.labelLarge) })
        }
        if (tab == 0) ProgressSection(ctx, now) else SystemSection(ctx, now, a11y, actions)
    }
}
