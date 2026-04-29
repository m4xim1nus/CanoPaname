package app.arbre.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * Une des 4 saisons calendaires fixes (déc-fév / mar-mai / juin-août / sep-nov),
 * indépendante de l'année.
 *
 * API canonique de la saisonnalité — Phase 3 l'utilise pour un tinting
 * décoratif du surface, Sprint I la réutilisera tel quel pour le bucket de
 * captures.
 */
enum class Season {
    WINTER,
    SPRING,
    SUMMER,
    AUTUMN;

    companion object {
        /** Saison courante au moment de l'appel (zone système). */
        fun current(clock: Clock = Clock.systemDefaultZone()): Season =
            fromInstant(clock.instant(), clock.zone)

        /** Saison du [instant] dans la [zone] donnée. */
        fun fromInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Season {
            val month = instant.atZone(zone).monthValue
            return when (month) {
                12, 1, 2 -> WINTER
                3, 4, 5 -> SPRING
                6, 7, 8 -> SUMMER
                9, 10, 11 -> AUTUMN
                else -> error("month out of range: $month")
            }
        }
    }
}
