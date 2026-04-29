package app.arbre.data

import java.time.Clock
import java.time.Instant
import java.time.Month
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Saisons calendaires fixes (déc-fév / mar-mai / juin-août / sep-nov),
 * indépendantes de l'année. Une espèce capturée à 2 saisons compte pour 2
 * entrées dans le Pokédex saisonnier ; les captures s'accumulent au fil
 * des années dans le bucket de leur saison (cf. ROADMAP Sprint I).
 *
 * Stockage : `CaptureEntity.season: Int` reste tel quel pour ne pas migrer
 * la table — l'`ordinal` de l'enum est aligné avec les anciennes constantes
 * (WINTER=0, SPRING=1, SUMMER=2, AUTUMN=3).
 */
enum class Season(val label: String) {
    WINTER("Hiver"),
    SPRING("Printemps"),
    SUMMER("Été"),
    AUTUMN("Automne");

    val storedValue: Int get() = ordinal

    companion object {
        fun fromStored(value: Int): Season = entries[value]

        fun fromTimestamp(epochMillis: Long): Season {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply { time = Date(epochMillis) }
            return when (cal.get(Calendar.MONTH)) {
                Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> WINTER
                Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> SPRING
                Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> SUMMER
                else -> AUTUMN
            }
        }

        fun fromInstant(instant: Instant, clock: Clock = Clock.systemDefaultZone()): Season {
            val zdt = ZonedDateTime.ofInstant(instant, clock.zone)
            return when (zdt.month) {
                Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> WINTER
                Month.MARCH, Month.APRIL, Month.MAY -> SPRING
                Month.JUNE, Month.JULY, Month.AUGUST -> SUMMER
                else -> AUTUMN
            }
        }

        /**
         * Saison courante calculée à la volée — pas de cache. Le coût est nul
         * et on évite les bugs de bascule à minuit (cf. ROADMAP Sprint I).
         */
        fun current(clock: Clock = Clock.systemDefaultZone()): Season =
            fromInstant(clock.instant(), clock)
    }
}
