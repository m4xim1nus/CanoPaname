package app.arbre.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** `arbres-export-yyyyMMdd.zip` zone Europe/Paris. */
fun defaultExportFilename(now: Long = System.currentTimeMillis()): String {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Europe/Paris")
    }
    return "arbres-export-${fmt.format(Date(now))}.zip"
}
