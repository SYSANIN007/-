package ru.sysanin.wearschedule.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Разбор страницы группы с публичного зеркала расписания УлГТУ
 * (timetable.житков.рф — туда выгружается то же расписание, что и в ЛК).
 *
 * Структура страницы: две таблицы — текущая и следующая неделя.
 * Каждая таблица: строки дней, в ячейках пары вида
 *   "пр.<br>Предмет<br>[1-я п/г]<br>Преподаватель<br>Аудитория"
 */
object MirrorParser {

    private val DAY_KEYS = linkedMapOf(
        "Пнд" to "mon", "Пн" to "mon",
        "Втр" to "tue", "Вт" to "tue",
        "Срд" to "wed", "Ср" to "wed",
        "Чтв" to "thu", "Чт" to "thu",
        "Птн" to "fri", "Пт" to "fri",
        "Сбт" to "sat", "Сб" to "sat",
        "Вск" to "sun", "Вс" to "sun",
    )

    private val WEEK_REGEX = Regex("Неделя[:\\s]+(\\d+)")
    private val WEEK_FALLBACK_REGEX = Regex("(\\d+)\\s*-я\\s*\\(")
    private val DATE_REGEX = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})")
    private val TIME_REGEX = Regex("\\d{1,2}:\\d{2}")
    private val BR_REGEX = Regex("(?i)<br\\s*/?>")

    private val TYPE_REGEX = Regex(
        """^(лек\.?|лекция|лекции|пр\.?|практическое занятие\s*\d*|лаб\.?|""" +
            """лабораторная работа|консультация|зачёт|зачет|экзамен|""" +
            """дифференцированный зачёт|дифференцированный зачет)$""",
        RegexOption.IGNORE_CASE,
    )

    private val SUBGROUP_REGEX = Regex("""^\d(?:-я)?\s*п/г\.?$""", RegexOption.IGNORE_CASE)
    private val ROOM_REGEX = Regex("""^\S{1,5}-\S{1,8}$""")

    /** @return готовый DTO или null, если страницу разобрать не удалось */
    fun parse(html: String): ScheduleDto? {
        val doc = Jsoup.parse(html)
        val tables = doc.select("table")
        if (tables.isEmpty()) return null

        val dayLessons = mutableMapOf<String, MutableList<LessonDto>>()
        val allBells = mutableListOf<BellDto>()
        var semesterStart: LocalDate? = null
        var groupTitle: String? = null

        for (table in tables) {
            val weekNumber = findWeekNumber(table) ?: continue
            val parity = if (weekNumber % 2 == 0) "even" else "odd"
            var weekMonday: LocalDate? = null

            for (row in table.select("tr")) {
                val cells = row.select("td")
                if (cells.isEmpty()) continue
                val head = cells.first().text()

                // Строка «Время | 08:30–09:50 | …» — расписание звонков
                if (head.contains("Время", ignoreCase = true)) {
                    val bells = parseBellRow(cells)
                    if (bells.isNotEmpty() && allBells.isEmpty()) allBells.addAll(bells)
                    continue
                }

                val dayKey = DAY_KEYS.entries.firstOrNull { head.startsWith(it.key, ignoreCase = true) }?.value
                    ?: continue

                val date = DATE_REGEX.find(head)?.let { m ->
                    runCatching {
                        LocalDate.of(
                            m.groupValues[3].toInt(),
                            m.groupValues[2].toInt(),
                            m.groupValues[1].toInt(),
                        )
                    }.getOrNull()
                }
                if (date != null && weekMonday == null) {
                    weekMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    if (semesterStart == null) {
                        semesterStart = weekMonday.minusWeeks((weekNumber - 1).toLong())
                    }
                }

                val list = dayLessons.getOrPut(dayKey) { mutableListOf() }
                for (i in 1 until cells.size) {
                    list += parseCell(cells[i].html(), pair = i, parity = parity)
                }
            }
        }

        if (dayLessons.isEmpty()) return null
        if (groupTitle == null) groupTitle = findGroupTitle(doc)

        val days = ScheduleMapper.DAY_KEYS.associateWith { key ->
            (dayLessons[key] ?: emptyList())
                .distinct()
                .sortedWith(compareBy({ it.pair }, { it.subgroup }))
        }

        return ScheduleDto(
            group = groupTitle ?: "Пдп-21 · группа 326",
            college = "КЭИ УлГТУ",
            semesterStart = (semesterStart ?: ScheduleMapper.DEFAULT_SEMESTER_START).toString(),
            updated = "зеркало ЛК, ${LocalDate.now()}",
            bells = allBells.ifEmpty { defaultBells() },
            days = days,
        )
    }

    /* ------------------------- внутренности ------------------------- */

    private fun findWeekNumber(table: Element): Int? {
        var sibling: Element? = table.previousElementSibling()
        var hops = 0
        while (sibling != null && hops < 10) {
            val text = sibling.text()
            WEEK_REGEX.find(text)?.let { return it.groupValues[1].toIntOrNull() }
            WEEK_FALLBACK_REGEX.find(text)?.let { return it.groupValues[1].toIntOrNull() }
            sibling = sibling.previousElementSibling()
            hops++
        }
        return null
    }

    private fun findGroupTitle(doc: Document): String? = runCatching {
        val text = doc.body().text()
        val idx = text.indexOf("учебной группы:", ignoreCase = true)
        if (idx < 0) return null
        text.substring(idx + "учебной группы:".length)
            .substringBefore("Неделя")
            .trim()
            .take(30)
            .ifEmpty { null }
    }.getOrNull()

    private fun parseBellRow(cells: List<Element>): List<BellDto> =
        cells.drop(1).mapNotNull { cell ->
            val times = TIME_REGEX.findAll(cell.text()).map { it.value }.toList()
            if (times.size >= 2) BellDto(normalizeTime(times[0]), normalizeTime(times[1])) else null
        }

    private fun normalizeTime(t: String): String {
        val parts = t.split(":")
        return String.format(Locale.US, "%02d:%s", parts[0].toInt(), parts[1])
    }

    /** Ячейка таблицы → 0..n занятий (в одной ячейке бывают 2 подгруппы сразу) */
    private fun parseCell(html: String, pair: Int, parity: String): List<LessonDto> {
        val lines = html.split(BR_REGEX)
            .map { Jsoup.parse(it).text().replace('\u00A0', ' ').trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        val blocks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            if (TYPE_REGEX.matches(line) && current.isNotEmpty()) {
                blocks += current
                current = mutableListOf()
            }
            current.add(line)
        }
        if (current.isNotEmpty()) blocks += current

        return blocks.mapNotNull { blockToLesson(it, pair, parity) }
    }

    private fun blockToLesson(block: List<String>, pair: Int, parity: String): LessonDto? {
        if (block.size < 2) return null
        val kind = normalizeKind(block[0])
        val subject = block[1].trim()
        if (subject.isEmpty() || TYPE_REGEX.matches(subject)) return null

        var room = ""
        var subgroup = ""
        var teacher = ""

        val rest = block.drop(2).toMutableList()
        if (rest.isNotEmpty() && ROOM_REGEX.matches(rest.last())) {
            room = rest.removeAt(rest.size - 1)
        }
        when (rest.size) {
            2 -> {
                if (SUBGROUP_REGEX.matches(rest[0])) {
                    subgroup = rest[0]
                    teacher = rest[1]
                } else {
                    // нет подгруппы: считаем обе строки преподавателем
                    teacher = rest.joinToString(" ").trim()
                }
            }
            1 -> {
                if (SUBGROUP_REGEX.matches(rest[0])) subgroup = rest[0] else teacher = rest[0]
            }
        }

        return LessonDto(
            pair = pair,
            subject = subject,
            kind = kind,
            subgroup = subgroup,
            teacher = teacher,
            room = room,
            week = parity,
        )
    }

    private fun normalizeKind(raw: String): String {
        val k = raw.trim().lowercase(Locale("ru"))
        return when {
            k.startsWith("лек") -> "лек."
            k.startsWith("практ") -> "пр."
            k.startsWith("пр") -> "пр."
            k.startsWith("лаб") -> "лаб."
            k.startsWith("конс") -> "конс."
            else -> ""
        }
    }

    private fun defaultBells(): List<BellDto> = ScheduleMapper.DEFAULT_BELLS.map {
        BellDto(it.first, it.second)
    }
}
