package com.example.acidwallet.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ru = Locale("ru", "RU")

/** «14.03.2026 18:42» — единый формат времени в приложении. */
fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", ru).format(Date(millis))

/** «14.03, 18:42» — компактный формат для списков. */
fun formatShort(millis: Long): String =
    SimpleDateFormat("dd.MM, HH:mm", ru).format(Date(millis))

/** «14.03.2026». */
fun formatDate(millis: Long): String =
    SimpleDateFormat("dd.MM.yyyy", ru).format(Date(millis))
