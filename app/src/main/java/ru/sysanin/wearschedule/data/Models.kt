package ru.sysanin.wearschedule.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* ------------------------------------------------------------------ */
/*  DTO — то, как расписание хранится в assets/schedule.json          */
/* ------------------------------------------------------------------ */

@Serializable
data class ScheduleDto(
    val group: String = "",
    val college: String = "",
    /** Понедельник первой недели семестра, yyyy-MM-dd */
    val semesterStart: String = "",
    val updated: String = "",
    val bells: List<BellDto> = emptyList(),
    val days: Map<String, List<LessonDto>> = emptyMap(),
)

@Serializable
data class BellDto(
    val start: String,
    val end: String,
)

@Serializable
data class LessonDto(
    /** Номер пары 1..8 (время берётся из bells) */
    val pair: Int = 0,
    /** Можно задать время вручную, минуя номер пары */
    val start: String = "",
    val end: String = "",
    val subject: String = "",
    /** лек. / пр. / лаб. */
    val kind: String = "",
    val subgroup: String = "",
    val teacher: String = "",
    val room: String = "",
    /** "all" (каждую неделю) | "even" (чётная неделя) | "odd" (нечётная) */
    val week: String = "all",
)

/* ------------------------------------------------------------------ */
/*  Доменные модели                                                   */
/* ------------------------------------------------------------------ */

enum class Parity { BOTH, EVEN, ODD }

data class Lesson(
    val pair: Int,
    val start: LocalTime,
    val end: LocalTime,
    val subject: String,
    val shortSubject: String,
    val kind: String,
    val subgroup: String,
    val teacher: String,
    val room: String,
    val parity: Parity,
) {
    val startStr: String get() = start.format(DISPLAY_TIME)
    val endStr: String get() = end.format(DISPLAY_TIME)
    val kindShort: String
        get() = when (kind.trim().lowercase()) {
            "лек.", "лекция", "лекции" -> "лек"
            "пр.", "практическое занятие" -> "пр"
            "лаб.", "лабораторная работа" -> "лаб"
            else -> kind.trim()
        }
    val subgroupShort: String
        get() = subgroup.replace("-я п/г", " п/г").ifBlank { "" }
}

data class Schedule(
    val group: String,
    val college: String,
    val semesterStart: LocalDate,
    val updated: String,
    val bells: List<Pair<LocalTime, LocalTime>>,
    /** ключи: mon..sun */
    val days: Map<String, List<Lesson>>,
) {
    fun lessonsOfWeekday(key: String): List<Lesson> = days[key].orEmpty()
}

private val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

/** Сокращения слишком длинных названий предметов для маленького экрана */
private val ABBREVIATIONS = mapOf(
    "Иностранный язык в профессиональной деятельности" to "Иностранный язык",
    "Теория вероятностей и математическая статистика" to "Теория вероятностей",
    "Технология разработки и защиты баз данных" to "Разработка и защита БД",
    "Операционные системы и среды" to "Операционные системы",
    "Разработка мобильных приложений" to "Разработка моб. приложений",
)

object ScheduleMapper {

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    val DEFAULT_BELLS = listOf(
        "08:30" to "09:50",
        "10:00" to "11:20",
        "11:30" to "12:50",
        "13:30" to "14:50",
        "15:00" to "16:20",
        "16:30" to "17:50",
        "18:00" to "19:20",
        "19:30" to "20:50",
    )

    fun fromDto(dto: ScheduleDto): Schedule {
        val bells = parseBells(dto.bells)
        val days = DAY_KEYS.mapNotNull { key ->
            val lessons = dto.days[key].orEmpty()
                .mapNotNull { toLesson(it, bells) }
                .distinct()
                .sortedWith(compareBy({ it.start }, { it.subgroup }))
            key to lessons
        }.toMap()
        return Schedule(
            group = dto.group.ifBlank { "Пдп-21 · группа 326" },
            college = dto.college.ifBlank { "КЭИ УлГТУ" },
            semesterStart = runCatching { LocalDate.parse(dto.semesterStart, ISO_DATE) }
                .getOrElse { DEFAULT_SEMESTER_START },
            updated = dto.updated,
            bells = bells,
            days = days,
        )
    }

    fun toDto(schedule: Schedule): ScheduleDto = ScheduleDto(
        group = schedule.group,
        college = schedule.college,
        semesterStart = schedule.semesterStart.toString(),
        updated = schedule.updated,
        bells = schedule.bells.map { BellDto(it.first.toString(), it.second.toString()) },
        days = schedule.days.mapValues { (_, lessons) ->
            lessons.map {
                LessonDto(
                    pair = it.pair,
                    start = it.start.toString(),
                    end = it.end.toString(),
                    subject = it.subject,
                    kind = it.kind,
                    subgroup = it.subgroup,
                    teacher = it.teacher,
                    room = it.room,
                    week = when (it.parity) {
                        Parity.BOTH -> "all"
                        Parity.EVEN -> "even"
                        Parity.ODD -> "odd"
                    },
                )
            }
        },
    )

    private fun parseBells(bells: List<BellDto>): List<Pair<LocalTime, LocalTime>> {
        val parsed = bells.mapNotNull { b ->
            val s = runCatching { LocalTime.parse(b.start) }.getOrNull()
            val e = runCatching { LocalTime.parse(b.end) }.getOrNull()
            if (s != null && e != null) s to e else null
        }
        return parsed.ifEmpty {
            DEFAULT_BELLS.map { (s, e) -> LocalTime.parse(s) to LocalTime.parse(e) }
        }
    }

    private fun toLesson(dto: LessonDto, bells: List<Pair<LocalTime, LocalTime>>): Lesson? {
        val subject = dto.subject.trim()
        if (subject.isEmpty()) return null

        val start: LocalTime?
        val end: LocalTime?
        if (dto.pair in 1..bells.size) {
            start = bells[dto.pair - 1].first
            end = bells[dto.pair - 1].second
        } else {
            start = runCatching { LocalTime.parse(dto.start) }.getOrNull()
            end = runCatching { LocalTime.parse(dto.end) }.getOrNull()
        }
        if (start == null || end == null) return null

        return Lesson(
            pair = dto.pair,
            start = start,
            end = end,
            subject = subject,
            shortSubject = ABBREVIATIONS[subject] ?: subject,
            kind = dto.kind.trim(),
            subgroup = dto.subgroup.trim(),
            teacher = dto.teacher.trim(),
            room = dto.room.trim(),
            parity = when (dto.week.trim().lowercase()) {
                "even", "чёт", "чет" -> Parity.EVEN
                "odd", "нечёт", "нечет" -> Parity.ODD
                else -> Parity.BOTH
            },
        )
    }

    /** Понедельник первой учебной недели (для вычисления чётности недель) */
    val DEFAULT_SEMESTER_START: LocalDate =
        LocalDate.parse("2026-08-31", ISO_DATE)

    val DAY_KEYS = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")

    fun dayKey(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "mon"
        DayOfWeek.TUESDAY -> "tue"
        DayOfWeek.WEDNESDAY -> "wed"
        DayOfWeek.THURSDAY -> "thu"
        DayOfWeek.FRIDAY -> "fri"
        DayOfWeek.SATURDAY -> "sat"
        DayOfWeek.SUNDAY -> "sun"
    }
}
