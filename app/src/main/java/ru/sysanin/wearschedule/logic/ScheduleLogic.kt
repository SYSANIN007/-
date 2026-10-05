package ru.sysanin.wearschedule.logic

import ru.sysanin.wearschedule.data.Lesson
import ru.sysanin.wearschedule.data.Parity
import ru.sysanin.wearschedule.data.Schedule
import ru.sysanin.wearschedule.data.ScheduleMapper
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Статус пары относительно текущего момента (имеет смысл только для «сегодня»). */
enum class LessonStatus { PAST, NOW, NEXT, FUTURE }

object ScheduleLogic {

    private val MONTHS_GENITIVE = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val MONTHS_SHORT = listOf(
        "янв", "фев", "мар", "апр", "мая", "июн",
        "июл", "авг", "сен", "окт", "ноя", "дек",
    )

    /* ------------------------------ недели ------------------------------ */

    /** Номер учебной недели семестра (1-я, 2-я, …) */
    fun semesterWeek(schedule: Schedule, date: LocalDate): Int {
        val start = schedule.semesterStart
            .let { start -> start.minusDays((start.dayOfWeek.value - 1).toLong()) } // понедельник 1-й недели
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val weeks = java.time.temporal.ChronoUnit.WEEKS.between(start, monday).toInt()
        return (weeks + 1).coerceAtLeast(1)
    }

    fun parityOf(week: Int): Parity = if (week % 2 == 0) Parity.EVEN else Parity.ODD

    fun parityOfDate(schedule: Schedule, date: LocalDate): Parity =
        parityOf(semesterWeek(schedule, date))

    /** «6-я неделя · чётная» */
    fun weekInfo(schedule: Schedule, date: LocalDate): String {
        val week = semesterWeek(schedule, date)
        val parity = if (week % 2 == 0) "чётная" else "нечётная"
        return "$week-я неделя · $parity"
    }

    /** Короткая метка чётности для TimeText */
    fun parityShort(schedule: Schedule, date: LocalDate): String =
        if (semesterWeek(schedule, date) % 2 == 0) "чёт" else "нечёт"

    /* ------------------------------ дни ------------------------------ */

    fun lessonsFor(schedule: Schedule, date: LocalDate): List<Lesson> {
        val key = ScheduleMapper.dayKey(date)
        val parity = parityOfDate(schedule, date)
        return schedule.lessonsOfWeekday(key)
            .filter { it.parity == Parity.BOTH || it.parity == parity }
    }

    /** Есть ли в этот день пары «другой» чётности (для подсказки на пустом дне) */
    fun otherParityLessons(schedule: Schedule, date: LocalDate): List<Lesson> {
        val key = ScheduleMapper.dayKey(date)
        val parity = parityOfDate(schedule, date)
        val other = if (parity == Parity.EVEN) Parity.ODD else Parity.EVEN
        return schedule.lessonsOfWeekday(key).filter { it.parity == other }
    }

    fun weekdayFull(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "Понедельник"
        DayOfWeek.TUESDAY -> "Вторник"
        DayOfWeek.WEDNESDAY -> "Среда"
        DayOfWeek.THURSDAY -> "Четверг"
        DayOfWeek.FRIDAY -> "Пятница"
        DayOfWeek.SATURDAY -> "Суббота"
        DayOfWeek.SUNDAY -> "Воскресенье"
    }

    fun weekdayShort(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            .replace(".", "")
            .uppercase()

    /** «7 октября» */
    fun dayMonth(date: LocalDate): String =
        "${date.dayOfMonth} ${MONTHS_GENITIVE[date.monthValue - 1]}"

    /** «7 окт» — для изогнутой строки времени */
    fun dayMonthShort(date: LocalDate): String =
        "${date.dayOfMonth} ${MONTHS_SHORT[date.monthValue - 1]}"

    /** Текст слева от часов: «СР 7 ОКТ» */
    fun timeTextLeading(date: LocalDate): String =
        "${weekdayShort(date)} ${dayMonthShort(date)}".uppercase()

    /* ------------------------------ статусы ------------------------------ */

    fun statusOf(lesson: Lesson, now: LocalTime): LessonStatus = when {
        now >= lesson.end -> LessonStatus.PAST
        now >= lesson.start -> LessonStatus.NOW
        else -> LessonStatus.FUTURE
    }

    fun currentLesson(lessons: List<Lesson>, now: LocalTime): Lesson? =
        lessons.firstOrNull { now >= it.start && now < it.end }

    fun nextLesson(lessons: List<Lesson>, now: LocalTime): Lesson? =
        lessons.firstOrNull { it.start > now }

    /** Индекс элемента списка, который надо отцентрировать при открытии дня. */
    fun initialCenterIndex(lessons: List<Lesson>, isToday: Boolean, now: LocalTime): Int {
        val lessonsStartAt = if (isToday) 2 else 1 // шапка (+ карточка «сейчас»)
        if (lessons.isEmpty()) return 0
        if (!isToday) return lessonsStartAt

        val currentIdx = lessons.indexOfFirst { now >= it.start && now < it.end }
        if (currentIdx >= 0) return lessonsStartAt + currentIdx

        val nextIdx = lessons.indexOfFirst { it.start > now }
        if (nextIdx >= 0) return lessonsStartAt + nextIdx

        return 1 // все пары прошли — центрируем карточку «на сегодня всё»
    }

    /* ------------------------------ формат ------------------------------ */

    /** «45 мин», «1 ч 20 мин» */
    fun formatDuration(minutes: Long): String = when {
        minutes <= 0 -> "меньше минуты"
        minutes < 60 -> "$minutes мин"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h ч" else "$h ч ${if (m < 10) "0" else ""}$m мин"
        }
    }

    fun minutesBetween(from: LocalTime, to: LocalTime): Long =
        Duration.between(from, to).toMinutes().coerceAtLeast(0)
}
