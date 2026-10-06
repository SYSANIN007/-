package ru.sysanin.wearschedule.ui

import androidx.compose.ui.graphics.Color
import ru.sysanin.wearschedule.data.Lesson
import ru.sysanin.wearschedule.data.Parity

/**
 * Палитра приложения.
 *
 * Основа — чистый чёрный фон (OLED) и глубокие тёмно-синие карточки.
 * Акценты:
 *  - чётная неделя  → синий   (#7DC8FF)
 *  - нечётная неделя → фиолетовый (#C4ADFF)
 *  - типы пар: лекция → синий, практика → бирюзовый, лаба → янтарный
 */
object Palette {

    /** Фон карточки */
    val Card = Color(0xFF141B2B)

    /** Тонкая рамка карточки (~8% белого) */
    val CardBorder = Color(0x16FFFFFF)

    /** Акцент чётной недели / основной */
    val Blue = Color(0xFF7DC8FF)

    /** Акцент нечётной недели */
    val Violet = Color(0xFFC4ADFF)

    /** Практика */
    val Teal = Color(0xFF5FD9A6)

    /** Лабораторная */
    val Amber = Color(0xFFFFC46B)

    /** Ошибки */
    val Red = Color(0xFFFF7A8A)

    /** Вторичный текст */
    val Dim = Color(0xFF9AA6BF)

    /** Цвет акцента для недели с такой чётностью */
    fun parity(parity: Parity): Color = when (parity) {
        Parity.EVEN -> Blue
        Parity.ODD -> Violet
        Parity.BOTH -> Blue
    }

    /** Цвет полоски типа занятия */
    fun kind(lesson: Lesson): Color = when (lesson.kindShort) {
        "лек" -> Blue
        "пр" -> Teal
        "лаб" -> Amber
        else -> Dim
    }
}
