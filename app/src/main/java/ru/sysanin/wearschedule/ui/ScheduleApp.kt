package ru.sysanin.wearschedule.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.material.scrollAway
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import ru.sysanin.wearschedule.data.Schedule
import ru.sysanin.wearschedule.logic.ScheduleLogic
import java.time.LocalDate
import java.time.LocalTime

/** Состояние фонной синхронизации с зеркалом расписания. */
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Error(val message: String) : SyncState
}

/** Сегодня + 13 дней вперёд (две учебные недели с разной чётностью). */
private const val PAGE_COUNT = 14

/**
 * Главный экран: страницы-дни, между которыми листают свайпом.
 *
 * Дизайн под маленький круглый экран:
 *  - вместо таблицы — вертикальный список пар (карточек);
 *  - вместо заголовков столбцов — время прямо на карточке пары;
 *  - верхняя изогнутая строка показывает день и чётность недели;
 *  - правая шкала — позиция в списке (безель/свайп).
 */
@Composable
fun ScheduleApp(
    schedule: Schedule,
    syncState: SyncState,
    onRefresh: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }

    // Тикаем раз в 30 секунд: подсветка «идёт сейчас» и обратный отсчёт.
    LaunchedEffect(Unit) {
        while (isActive) {
            now = LocalTime.now()
            today = LocalDate.now()
            delay(30_000)
        }
    }

    val pagerState = rememberPagerState(initialPage = 0) { PAGE_COUNT }
    val dates = remember(today) { List(PAGE_COUNT) { today.plusDays(it.toLong()) } }

    // Отдельное состояние списка на каждый день, чтобы запоминать позицию
    // прокрутки и сразу открывать день на текущей/следующей паре.
    // (Обычный цикл: composable-вызовы в лямбде List{} запрещены.)
    val listStates = ArrayList<ScalingLazyListState>(PAGE_COUNT)
    for (page in 0 until PAGE_COUNT) {
        val lessons = ScheduleLogic.lessonsFor(schedule, dates[page])
        listStates += rememberScalingLazyListState(
            initialCenterItemIndex = ScheduleLogic.initialCenterIndex(
                lessons = lessons,
                isToday = page == 0,
                now = now,
            ),
        )
    }

    val currentPage = pagerState.currentPage
    val currentListState = listStates[currentPage]

    Scaffold(
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = currentListState) },
        timeText = {
            val date = dates[currentPage]
            // Цвет верхней строки — акцент текущей страницы:
            // синий на чётной неделе, фиолетовый на нечётной.
            val accent = Palette.parity(ScheduleLogic.parityOfDate(schedule, date))
            val leading = ScheduleLogic.timeTextLeading(date)
            val trailing = ScheduleLogic.parityShort(schedule, date)
            val timeStyle = TimeTextDefaults.timeTextStyle(color = accent)

            TimeText(
                modifier = Modifier.scrollAway(currentListState),
                startLinearContent = { Text(text = leading, style = timeStyle) },
                startCurvedContent = {
                    curvedText(text = leading, style = CurvedTextStyle(timeStyle))
                },
                endLinearContent = { Text(text = trailing, style = timeStyle) },
                endCurvedContent = {
                    curvedText(text = trailing, style = CurvedTextStyle(timeStyle))
                },
            )
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            DayPage(
                schedule = schedule,
                date = dates[page],
                isToday = page == 0,
                now = if (page == 0) now else null,
                listState = listStates[page],
                syncState = syncState,
                onRefresh = onRefresh,
            )
        }
    }
}
