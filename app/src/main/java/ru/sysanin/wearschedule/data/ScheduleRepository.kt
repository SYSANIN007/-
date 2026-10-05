package ru.sysanin.wearschedule.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Хранилище расписания.
 *
 * Приоритет источника при запуске:
 *  1. assets/schedule.json — если его содержимое изменилось с прошлого запуска
 *     (то есть разработчик отредактировал файл и пересобрал приложение);
 *  2. последний успешный результат синхронизации с зеркалом ЛК УлГТУ;
 *  3. assets/schedule.json без изменений;
 *  4. встроенный резервный минимум (чтобы приложение никогда не падало).
 */
object ScheduleRepository {

    private const val PREFS = "schedule_prefs"
    private const val KEY_SYNCED_JSON = "synced_json"
    private const val KEY_SYNCED_AT = "synced_at_millis"
    private const val KEY_ASSETS_HASH = "assets_hash"
    private const val STALE_AFTER_MS = 6 * 60 * 60 * 1000L // 6 часов

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(context: Context): Schedule {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val assetsJson = readAssets(context)
        val assetsSchedule = assetsJson?.let { parse(it) }

        if (assetsJson != null && assetsSchedule != null) {
            val hash = assetsJson.hashCode()
            if (prefs.getInt(KEY_ASSETS_HASH, Int.MIN_VALUE) != hash) {
                // Файл в assets изменился (разработчик отредактировал и пересобрал) —
                // он важнее сохранённой синхронизации. Сдвигаем время последней
                // синхронизации, чтобы тихое автообновление его сразу не затёрло.
                prefs.edit()
                    .putInt(KEY_ASSETS_HASH, hash)
                    .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
                    .remove(KEY_SYNCED_JSON)
                    .apply()
                return assetsSchedule
            }
        }

        // Последняя успешная синхронизация с зеркалом.
        prefs.getString(KEY_SYNCED_JSON, null)?.let { saved ->
            parse(saved)?.let { return it }
        }

        // assets как офлайн-источник по умолчанию.
        if (assetsSchedule != null) return assetsSchedule

        return FallbackSchedule.schedule
    }

    fun parse(text: String): Schedule? = runCatching {
        ScheduleMapper.fromDto(json.decodeFromString(ScheduleDto.serializer(), text))
    }.getOrNull()

    /** Нужно ли тихо обновиться при запуске */
    fun isStale(context: Context): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SYNCED_AT, 0L)
        return System.currentTimeMillis() - last > STALE_AFTER_MS
    }

    fun save(context: Context, dto: ScheduleDto): Schedule {
        val schedule = ScheduleMapper.fromDto(dto)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SYNCED_JSON, json.encodeToString(ScheduleDto.serializer(), dto))
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .apply()
        return schedule
    }

    private fun readAssets(context: Context): String? = runCatching {
        context.assets.open("schedule.json").bufferedReader().use { it.readText() }
    }.getOrNull()
}

/** Аварийный минимум, если assets/schedule.json удалён или битый. */
private object FallbackSchedule {
    val schedule: Schedule by lazy {
        ScheduleMapper.fromDto(
            ScheduleDto(
                group = "Пдп-21 · группа 326",
                college = "КЭИ УлГТУ",
                semesterStart = ScheduleMapper.DEFAULT_SEMESTER_START.toString(),
                updated = "резервные данные",
                days = mapOf(
                    "mon" to listOf(
                        LessonDto(
                            pair = 3,
                            subject = "Проверь assets/schedule.json",
                            kind = "",
                            room = "",
                            week = "all",
                        )
                    )
                ),
            )
        )
    }
}
