package ru.sysanin.wearschedule.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import ru.sysanin.wearschedule.data.Lesson
import ru.sysanin.wearschedule.data.Parity
import ru.sysanin.wearschedule.logic.LessonStatus
import ru.sysanin.wearschedule.logic.ScheduleLogic
import java.time.LocalTime

/**
 * Компактная карточка одной пары — «строка таблицы», переосмысленная под часы:
 *
 *   ┌──────────────────────────────┐
 *   │ 11:30   Разработка моб.      │
 *   │ 12:50   приложений           │
 *   │         пр · 6-329 · 1 п/г   │
 *   └──────────────────────────────┘
 *
 * Тап по карточке раскрывает преподавателя и прочие детали.
 */
@Composable
fun LessonCard(
    lesson: Lesson,
    status: LessonStatus,
    now: LocalTime?,
) {
    var expanded by remember { mutableStateOf(false) }

    val borderColor = when (status) {
        LessonStatus.NOW -> MaterialTheme.colors.primary
        LessonStatus.NEXT -> MaterialTheme.colors.secondary.copy(alpha = 0.60f)
        else -> null
    }
    val container = when (status) {
        LessonStatus.NOW -> MaterialTheme.colors.primary.copy(alpha = 0.13f)
        else -> MaterialTheme.colors.surface
    }
    val contentAlpha = if (status == LessonStatus.PAST) 0.45f else 1f

    val meta = listOfNotNull(
        lesson.kindShort.takeIf { it.isNotBlank() },
        lesson.room.takeIf { it.isNotBlank() },
        lesson.subgroupShort.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(contentAlpha)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .then(
                if (borderColor != null) {
                    Modifier.border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column {
                Text(
                    text = lesson.startStr,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = if (status == LessonStatus.NOW) {
                        MaterialTheme.colors.primary
                    } else {
                        MaterialTheme.colors.onSurface
                    },
                )
                Text(
                    text = lesson.endStr,
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.shortSubject,
                    style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.80f),
                    )
                }

                if (status == LessonStatus.NOW && now != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "идёт · ещё " +
                            ScheduleLogic.formatDuration(
                                ScheduleLogic.minutesBetween(now, lesson.end),
                            ),
                        style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colors.primary,
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(2.dp))
                    if (lesson.teacher.isNotBlank()) {
                        Text(
                            text = lesson.teacher,
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.85f),
                        )
                    }
                    if (lesson.subgroup.isNotBlank()) {
                        Text(
                            text = lesson.subgroup,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
                        )
                    }
                    if (lesson.parity != Parity.BOTH) {
                        Text(
                            text = if (lesson.parity == Parity.EVEN) "чётная неделя" else "нечётная неделя",
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.70f),
                        )
                    }
                    if (lesson.subject != lesson.shortSubject) {
                        Text(
                            text = lesson.subject,
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.60f),
                        )
                    }
                }
            }
        }
    }
}
