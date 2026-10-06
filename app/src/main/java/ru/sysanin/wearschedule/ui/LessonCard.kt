package ru.sysanin.wearschedule.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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
 *   ┌───────────────────────────────┐
 *   │ ▌ 11:30  Разработка моб.      │
 *   │ ▌ 12:50  приложений           │
 *   │ ▌        3-я · пр · 6-329     │
 *   └───────────────────────────────┘
 *    ↑ цветная полоска = тип занятия (лек/пр/лаб)
 *
 * Тап по карточке раскрывает преподавателя и прочие детали.
 * Идущая сейчас пара подсвечена рамкой и градиентом цвета недели.
 */
@Composable
fun LessonCard(
    lesson: Lesson,
    status: LessonStatus,
    now: LocalTime?,
    accent: Color,
) {
    var expanded by remember { mutableStateOf(false) }

    val isActive = status == LessonStatus.NOW
    val borderColor: Color
    val borderWidth: Dp
    when (status) {
        LessonStatus.NOW -> {
            borderColor = accent
            borderWidth = 2.dp
        }
        LessonStatus.NEXT -> {
            borderColor = accent.copy(alpha = 0.55f)
            borderWidth = 1.5.dp
        }
        else -> {
            borderColor = Palette.CardBorder
            borderWidth = 1.dp
        }
    }

    val container = if (isActive) {
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.04f)),
        )
    } else {
        Brush.verticalGradient(listOf(Palette.Card, Palette.Card.copy(alpha = 0.60f)))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (status == LessonStatus.PAST) 0.42f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp))
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {

            // цветная полоска типа занятия
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.kind(lesson)),
            )

            Spacer(Modifier.width(10.dp))

            // время
            Column {
                Text(
                    text = lesson.startStr,
                    style = MaterialTheme.typography.title2.copy(fontWeight = FontWeight.Bold),
                    color = if (isActive) accent else MaterialTheme.colors.onSurface,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = lesson.endStr,
                    style = MaterialTheme.typography.caption1,
                    color = Palette.Dim,
                )
            }

            Spacer(Modifier.width(12.dp))

            // предмет и метаданные
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.shortSubject,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colors.onSurface,
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(3.dp))
                Text(
                    text = lessonMeta(lesson),
                    style = MaterialTheme.typography.caption1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isActive && now != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "ещё " + ScheduleLogic.formatDuration(
                            ScheduleLogic.minutesBetween(now, lesson.end),
                        ),
                        style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = accent,
                    )
                }

                if (expanded) {
                    Spacer(Modifier.height(5.dp))
                    if (lesson.teacher.isNotBlank()) {
                        Text(
                            text = lesson.teacher,
                            style = MaterialTheme.typography.caption1,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                    if (lesson.subgroup.isNotBlank()) {
                        Text(
                            text = lesson.subgroup,
                            style = MaterialTheme.typography.caption2,
                            color = Palette.Dim,
                        )
                    }
                    if (lesson.parity != Parity.BOTH) {
                        Text(
                            text = if (lesson.parity == Parity.EVEN) "чётная неделя" else "нечётная неделя",
                            style = MaterialTheme.typography.caption2,
                            color = Palette.Dim,
                        )
                    }
                    if (lesson.subject != lesson.shortSubject) {
                        Text(
                            text = lesson.subject,
                            style = MaterialTheme.typography.caption2,
                            color = Palette.Dim.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

/** Цветная строка метаданных: «3-я · пр · 6-329 · 1 п/г» */
private fun lessonMeta(lesson: Lesson) = buildAnnotatedString {
    var first = true

    fun separator() {
        if (!first) {
            withStyle(SpanStyle(color = Color.White.copy(alpha = 0.30f))) { append("  ·  ") }
        }
        first = false
    }

    if (lesson.pair > 0) {
        separator()
        withStyle(SpanStyle(color = Palette.Dim)) { append("${lesson.pair}-я") }
    }
    if (lesson.kindShort.isNotBlank()) {
        separator()
        withStyle(
            SpanStyle(color = Palette.kind(lesson), fontWeight = FontWeight.Bold),
        ) { append(lesson.kindShort) }
    }
    if (lesson.room.isNotBlank()) {
        separator()
        withStyle(SpanStyle(color = Color.White.copy(alpha = 0.92f))) { append(lesson.room) }
    }
    if (lesson.subgroupShort.isNotBlank()) {
        separator()
        withStyle(SpanStyle(color = Palette.Dim)) { append(lesson.subgroupShort) }
    }
}
