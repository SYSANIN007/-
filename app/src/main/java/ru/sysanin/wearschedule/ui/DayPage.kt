package ru.sysanin.wearschedule.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
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

    val accent = Palette.parity(ScheduleLogic.parityOfDate(schedule, date))
    val lessons = remember(schedule, date, accent) { ScheduleLogic.lessonsFor(schedule, date) }

    // Индексы текущей и следующей пары — для подсветки карточек.
    val currentIdx = if (now != null) lessons.indexOfFirst { now >= it.start && now < it.end } else -1
    val nextIdx = if (now != null) lessons.indexOfFirst { it.start > now } else -1

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = if (isRound) 16.dp else 10.dp,
            end = if (isRound) 16.dp else 10.dp,
            top = 26.dp,
            bottom = 26.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { DayHeader(schedule = schedule, date = date, isToday = isToday, accent = accent) }

        if (isToday && now != null) {
            item { TodayStatus(lessons = lessons, now = now, accent = accent) }
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
                    accent = accent,
                )
            }
        }

        item { Footer(schedule = schedule, syncState = syncState, onRefresh = onRefresh, accent = accent) }
    }
}

/* ------------------------------ шапка дня ------------------------------ */

@Composable
private fun DayHeader(schedule: Schedule, date: LocalDate, isToday: Boolean, accent: Color) {
    val title = when {
        isToday -> "Сегодня"
        date == LocalDate.now().plusDays(1) -> "Завтра"
        else -> ScheduleLogic.weekdayFull(date)
    }
    SectionCard(
        container = accent.copy(alpha = 0.10f),
        borderColor = accent.copy(alpha = 0.35f),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            color = accent,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "${ScheduleLogic.dayMonth(date)} · ${ScheduleLogic.weekInfo(schedule, date)}",
                style = MaterialTheme.typography.caption1,
                color = Palette.Dim,
            )
        }
    }
}

/* --------------------------- «сейчас / дальше» --------------------------- */

@Composable
private fun TodayStatus(lessons: List<Lesson>, now: LocalTime, accent: Color) {
    if (lessons.isEmpty()) return // пустой день покажет своя карточка

    val current = ScheduleLogic.currentLesson(lessons, now)
    val next = ScheduleLogic.nextLesson(lessons, now)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.05f)),
                ),
            )
            .border(2.dp, accent, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        when {
            current != null -> {
                StatusLabel("ИДЁТ СЕЙЧАС")
                Text(
                    text = current.shortSubject,
                    style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                    maxLines = 2,
                )
                Text(
                    text = metaLine(current),
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.85f),
                )

                Spacer(Modifier.height(7.dp))

                // прогресс прошедшей части пары
                val total = ScheduleLogic.minutesBetween(current.start, current.end).coerceAtLeast(1L)
                val elapsed = ScheduleLogic.minutesBetween(current.start, now)
                val fraction by animateFloatAsState(
                    targetValue = (elapsed.toFloat() / total).coerceIn(0f, 1f),
                    label = "lessonProgress",
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "ещё " + ScheduleLogic.formatDuration(
                            ScheduleLogic.minutesBetween(now, current.end),
                        ),
                        style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                }
            }

            next != null -> {
                StatusLabel("СЛЕДУЮЩАЯ ПАРА")
                Text(
                    text = next.shortSubject,
                    style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 2,
                )
                Text(
                    text = metaLine(next),
                    style = MaterialTheme.typography.caption1,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "в ${next.startStr} · через " +
                        ScheduleLogic.formatDuration(ScheduleLogic.minutesBetween(now, next.start)),
                    style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }

            else -> {
                StatusLabel("НА СЕГОДНЯ ВСЁ")
                Text(
                    text = "Пары закончились 🎉",
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                )
                Text(
                    text = "Отдыхай — завтра всё по новой",
                    style = MaterialTheme.typography.caption1,
                    color = Palette.Dim,
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
        color = Palette.Dim,
    )
}

private fun metaLine(lesson: Lesson): String = listOfNotNull(
    lesson.kindShort.takeIf { it.isNotBlank() },
    lesson.room.takeIf { it.isNotBlank() },
    lesson.subgroupShort.takeIf { it.isNotBlank() },
).joinToString(" · ")

/* ------------------------------ пустой день ------------------------------ */

@Composable
private fun EmptyDay(schedule: Schedule, date: LocalDate) {
    val other = ScheduleLogic.otherParityLessons(schedule, date)
    val hint = if (other.isNotEmpty()) {
        val otherName =
            if (ScheduleLogic.parityOfDate(schedule, date) == Parity.EVEN) "нечётную" else "чётную"
        "Но в $otherName неделю пары есть"
    } else {
        "Полный выходной"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🎉", fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Пар нет",
            style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.caption1,
            color = Palette.Dim,
            textAlign = TextAlign.Center,
        )
    }
}

/* -------------------------------- подвал -------------------------------- */

@Composable
private fun Footer(schedule: Schedule, syncState: SyncState, onRefresh: () -> Unit, accent: Color) {
    val running = syncState == SyncState.Running

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        when (val state = syncState) {
            SyncState.Running -> Text(
                text = "Обновление расписания…",
                style = MaterialTheme.typography.caption2,
                color = Palette.Dim,
            )

            is SyncState.Error -> Text(
                text = "Не удалось обновить: ${state.message}",
                style = MaterialTheme.typography.caption2,
                color = Palette.Red,
                textAlign = TextAlign.Center,
            )

            else -> {}
        }

        Spacer(Modifier.height(8.dp))

        // пилюля-кнопка обновления
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .border(
                    width = 1.dp,
                    color = if (running) Palette.Dim.copy(alpha = 0.35f) else accent.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(percent = 50),
                )
                .clickable(enabled = !running) { onRefresh() }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
            Text(
                text = if (running) "Обновление…" else "Обновить",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                color = if (running) Palette.Dim else accent,
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = schedule.group,
            style = MaterialTheme.typography.caption2,
            color = Palette.Dim,
        )
        Text(
            text = "${schedule.college} · ${schedule.updated}",
            style = MaterialTheme.typography.caption2,
            color = Palette.Dim.copy(alpha = 0.70f),
            textAlign = TextAlign.Center,
        )
    }
}
