package ru.sysanin.wearschedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Автообновление расписания с публичного зеркала ЛК УлГТУ.
 *
 * Оригинал (lk.ulstu.ru) требует входа в аккаунт, поэтому приложение берёт те же
 * данные с зеркала timetable.житков.рф, куда расписание выгружается в открытую.
 * Если зеркала не будет — приложение продолжит работать на локальных данных.
 */
object ScheduleSync {

    /** Страница группы на зеркале. Сменишь группу — поменяй номер здесь. */
    const val SOURCE_URL = "https://timetable.xn--b1ahgiuw.xn--p1ai/kei/326.html"

    suspend fun refresh(context: Context): Result<Schedule> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetch(SOURCE_URL)
            val dto = MirrorParser.parse(html)
                ?: error("Не удалось разобрать страницу расписания")
            ScheduleRepository.save(context, dto)
        }
    }

    private fun fetch(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) error("HTTP $code")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
