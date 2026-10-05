package ru.sysanin.wearschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.launch
import ru.sysanin.wearschedule.data.ScheduleRepository
import ru.sysanin.wearschedule.data.ScheduleSync
import ru.sysanin.wearschedule.ui.ScheduleApp
import ru.sysanin.wearschedule.ui.SyncState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val context = LocalContext.current
                var schedule by remember { mutableStateOf(ScheduleRepository.load(context)) }
                var syncState by remember { mutableStateOf<SyncState>(SyncState.Idle) }
                val scope = rememberCoroutineScope()

                fun refresh() {
                    if (syncState == SyncState.Running) return
                    scope.launch {
                        syncState = SyncState.Running
                        ScheduleSync.refresh(context)
                            .onSuccess { schedule = it; syncState = SyncState.Idle }
                            .onFailure { e ->
                                syncState = SyncState.Error(e.message ?: "нет сети")
                            }
                    }
                }

                // Тихо подтягиваем свежее расписание раз в 6 часов.
                LaunchedEffect(Unit) {
                    if (ScheduleRepository.isStale(context)) refresh()
                }

                ScheduleApp(
                    schedule = schedule,
                    syncState = syncState,
                    onRefresh = { refresh() },
                )
            }
        }
    }
}
