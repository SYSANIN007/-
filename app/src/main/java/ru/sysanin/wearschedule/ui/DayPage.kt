package ru.sysanin.wearschedule.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import ru.sysanin.wearschedule.data.Lesson
import ru.sysanin.wearschedule.data.Parity
import ru.sysanin.wearschedule.data.Schedule
import ru.sysanin.wearschedule.logic.LessonStatus
import ru.sysanin.wearschedule.logic.ScheduleLogic
import java.time.LocalDate
import java.time.LocalTime

/**
 * Один день = страница пейджера: шапка дня, карточка «сейчас/дальше»,
 * список пар и подвал с кнопкой обновления.
 */
@Composable
fun DayPage(
    schedule: Schedule,
    date: LocalDate,
    isToday: Boolean,
    now: LocalTime?,
    listState: ScalingLazyListState,
    syncState: SyncState,
    onRefresh: () -> Unit,
) {
    val isRound = (LocalConfiguration.current.screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK) ==
        Configuration.SCREENLAYOUT_ROUND_YES

    val lessons = remember(schedule, date) { ScheduleLogic.lessonsFor(schedule, date) }

    // Индексы текущей и следующей пары — для подсветки карточек.
    val currentIdx = if (now != null) lessons.indexOfFirst { now >= it.start && now < it.end } else -1
    val nextIdx = if (now != null) lessons.indexOfFirst { it.start > now } else -1

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = if (isRound) 14.dp else 8.dp,
            end = if (isRound) 14.dp else 8.dp,
            top = 24.dp,
            bottom = 24.dp,
        ),
    ) {
        item { DayHeader(schedule = schedule, date = date, isToday = isToday) }

        if (isToday && now != null) {
            item { TodayStatus(lessons = lessons, now = now) }
        }

        if (lessons.isEmpty()) {
            item { EmptyDay(schedule = schedule, date = date) }
        } else {
            items(lessons.size) { index ->
                val status = when {
                    now == null -> LessonStatus.FUTURE
                    index == currentIdx -> LessonStatus.NOW
                    index == nextIdx -> LessonStatus.NEXT
                    now >= lessons[index].end -> LessonStatus.PAST
                    else -> LessonStatus.FUTURE
                }
                LessonCard(
                    lesson = lessons[index],
                    status = status,
                    now = now,
                )
            }
        }

        item { Footer(schedule = schedule, syncState = syncState, onRefresh = onRefresh) }
    }
}

/* ------------------------------ шапка дня ------------------------------ */

@Composable
private fun DayHeader(schedule: Schedule, date: LocalDate, isToday: Boolean) {
    val title = when {
        isToday -> "Сегодня"
        date == LocalDate.now().plusDays(1) -> "Завтра"
        else -> ScheduleLogic.weekdayFull(date)
    }
    SectionCard(
        container = MaterialTheme.colors.primary.copy(alpha = 0.10f),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.primary,
        )
        Text(
            text = "${ScheduleLogic.dayMonth(date)} · ${ScheduleLogic.weekInfo(schedule, date)}",
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.80f),
        )
    }
}

/* --------------------------- «сейчас / дальше» --------------------------- */

@Composable
private fun TodayStatus(lessons: List<Lesson>, now: LocalTime) {
    if (lessons.isEmpty()) return // пустой день покажет своя карточка

    val current = ScheduleLogic.currentLesson(lessons, now)
    val next = ScheduleLogic.nextLesson(lessons, now)

    SectionCard(
        container = if (current != null) {
            MaterialTheme.colors.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colors.surface
        },
        borderColor = if (current != null) {
            MaterialTheme.colors.primary
        } else {
            MaterialTheme.colors.secondary.copy(alpha = 0.60f)
        },
    ) {
        when {
            current != null -> {
                StatusLabel("ИДЁТ СЕЙЧАС")
                Text(
                    text = current.shortSubject,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.primary,
                    maxLines = 2,
                )
                Text(
                    text = listOfNotNull(
                        current.room.takeIf { it.isNotBlank() },
                        "ещё ${ScheduleLogic.formatDuration(ScheduleLogic.minutesBetween(now, current.end))}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                )
            }

            next != null -> {
                StatusLabel("СЛЕДУЮЩАЯ ПАРА")
                Text(
                    text = next.shortSubject,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 2,
                )
                Text(
                    text = "в ${next.startStr} · через " +
                        ScheduleLogic.formatDuration(ScheduleLogic.minutesBetween(now, next.start)),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                )
            }

            else -> {
                StatusLabel("НА СЕГОДНЯ ВСЁ")
                Text(
                    text = "Пары закончились 🎉",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.60f),
    )
}

/* ------------------------------ пустой день ------------------------------ */

@Composable
private fun EmptyDay(schedule: Schedule, date: LocalDate) {
    val other = ScheduleLogic.otherParityLessons(schedule, date)
    val hint = if (other.isNotEmpty()) {
        val otherName = if (ScheduleLogic.parityOfDate(schedule, date) == Parity.EVEN) "нечётную" else "чётную"
        "Но в $otherName неделю пары есть"
    } else {
        "Полный выходной"
    }
    Section {
        Text(
            text = "Пар нет 🎉",
            style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.caption1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
        )
    }
}

/* -------------------------------- подвал -------------------------------- */

@Composable
private fun Footer(schedule: Schedule, syncState: SyncState, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        when (val state = syncState) {
            SyncState.Running -> Text(
                text = "Обновление расписания…",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
            )

            is SyncState.Error -> Text(
                text = "Не удалось обновить: ${state.message}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.error,
                textAlign = TextAlign.Center,
            )

            else -> {}
        }

        Spacer(Modifier.height(6.dp))

        Chip(
            onClick = onRefresh,
            label = { Text(text = "Обновить") },
            secondaryLabel = { Text(text = schedule.group) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(0.85f),
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "${schedule.college} · ${schedule.updated}",
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}
