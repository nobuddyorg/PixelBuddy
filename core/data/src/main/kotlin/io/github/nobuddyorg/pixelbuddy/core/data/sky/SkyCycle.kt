package io.github.nobuddyorg.pixelbuddy.core.data.sky

import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The phases of the sky that follows the sun (#28), like a retro game's day/night cycle. Between the four looks
 * (night, dawn, day, dusk) lies a half-and-half blend, so a phase change is a small step; with four changes a day
 * ([main]) the blends are left out. Nights can be almost black ([stops]).
 */
enum class SkyPhase {
    Night,
    EarlyDawn,
    Dawn,
    Morning,
    Day,
    Evening,
    Dusk,
    LateDusk,
    ;

    /** Gradient stops (ARGB), top to bottom, like [Sky.stops]. */
    val stops: List<Int> get() = stops(darkNight = false)

    /** Gradient stops; with [darkNight] the night is almost black, and so are the blends into it. */
    fun stops(darkNight: Boolean): List<Int> {
        val night = if (darkNight) DARK_NIGHT else NIGHT
        return when (this) {
            Night -> night
            EarlyDawn -> blend(night, DAWN)
            Dawn -> DAWN
            Morning -> blend(DAWN, DAY)
            Day -> DAY
            Evening -> blend(DAY, DUSK)
            Dusk -> DUSK
            LateDusk -> blend(DUSK, night)
        }
    }

    /** With four changes a day, the blended looks join their twilight: dawn and dusk last from night to day. */
    val main: SkyPhase
        get() =
            when (this) {
                EarlyDawn, Morning -> Dawn
                Evening, LateDusk -> Dusk
                else -> this
            }

    companion object {
        /** The phase for the sun at [sun]: twilight looks within a few degrees of the horizon. */
        fun of(sun: SunPosition): SkyPhase {
            val e = sun.elevation
            return when {
                e < NIGHT_BELOW -> Night
                e < TWILIGHT_BELOW -> if (sun.morning) EarlyDawn else LateDusk
                e < TWILIGHT_ABOVE -> if (sun.morning) Dawn else Dusk
                e < DAY_ABOVE -> if (sun.morning) Morning else Evening
                else -> Day
            }
        }

        /** The phase at [time]; with [fourSteps] only night, dawn, day and dusk. */
        fun at(
            time: Instant,
            place: GeoPoint,
            fourSteps: Boolean = false,
        ): SkyPhase = of(SunPosition.at(time, place)).let { if (fourSteps) it.main else it }

        /**
         * When the phase after [time] changes, to the minute; null if it doesn't within a day (polar day or night).
         * Home redraws the sky only then.
         */
        fun nextChange(
            time: Instant,
            place: GeoPoint,
            fourSteps: Boolean = false,
        ): Instant? {
            val now = at(time, place, fourSteps)
            var step = time
            val end = time.plus(Duration.ofDays(1))
            while (step < end) {
                step = step.plus(SEARCH_STEP)
                if (at(step, place, fourSteps) != now) {
                    // Back to the first minute of the new phase.
                    var minute = step.minus(SEARCH_STEP)
                    while (at(minute, place, fourSteps) == now) minute = minute.plusSeconds(60)
                    return minute
                }
            }
            return null
        }

        /** The phases a day shows, night to night. */
        fun day(fourSteps: Boolean): List<SkyPhase> = if (fourSteps) entries.map { it.main }.distinct() else entries

        /** Degrees of sun elevation where the looks change. */
        private const val NIGHT_BELOW = -10.0
        private const val TWILIGHT_BELOW = -4.0
        private const val TWILIGHT_ABOVE = 4.0
        private const val DAY_ABOVE = 10.0
        private val SEARCH_STEP = Duration.ofMinutes(5)

        private val NIGHT = Sky.Night.stops.toList()

        /** Almost black, a faint blue towards the bottom; light text stays readable. */
        internal val DARK_NIGHT =
            listOf(0xFF000000.toInt(), 0xFF03040C.toInt(), 0xFF070A1C.toInt(), 0xFF0D1230.toInt())
        private val DAWN = Sky.Dawn.stops.toList()
        private val DUSK = Sky.Dusk.stops.toList()

        /** Daylight blue: dark at the top for the clock, the bottom no brighter than the dusk's orange. */
        internal val DAY = listOf(0xFF14275C.toInt(), 0xFF24508F.toInt(), 0xFF3572A8.toInt(), 0xFF4F8FBF.toInt())

        private fun blend(
            a: List<Int>,
            b: List<Int>,
        ): List<Int> = a.zip(b) { x, y -> mix(x, y) }

        private fun mix(
            x: Int,
            y: Int,
        ): Int {
            fun channel(shift: Int) = (((x shr shift and 0xFF) + (y shr shift and 0xFF)) / 2.0).roundToInt()
            return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
    }
}

/** Where the sky follows the sun from: the weather's place, else a rough guess from the time zone. */
object SkyLocation {
    /**
     * [weatherPlace] is the weather's place or its last approximate location; without one, the [zone]'s standard
     * offset gives the longitude (15° per hour) and a mid latitude of the right hemisphere is assumed, which is
     * enough to get sunrise and sunset within an hour or so.
     */
    fun resolve(
        weatherPlace: GeoPoint?,
        zone: ZoneId,
        now: Instant,
    ): GeoPoint {
        if (weatherPlace != null) return weatherPlace
        val offsetHours = zone.rules.getStandardOffset(now).totalSeconds / 3600.0
        val southern = SOUTHERN_ZONES.any { zone.id.startsWith(it) }
        return GeoPoint(latitude = if (southern) -SOUTH_LATITUDE else NORTH_LATITUDE, longitude = offsetHours * 15)
    }

    private const val NORTH_LATITUDE = 48.0
    private const val SOUTH_LATITUDE = 33.0

    /** Time zones south of the equator (most of them): southern summer is in December. */
    private val SOUTHERN_ZONES =
        listOf(
            "Australia/",
            "Antarctica/",
            "Pacific/Auckland",
            "Pacific/Chatham",
            "Pacific/Fiji",
            "Pacific/Tongatapu",
            "Pacific/Noumea",
            "America/Argentina",
            "America/Buenos_Aires",
            "America/Santiago",
            "America/Sao_Paulo",
            "America/Montevideo",
            "America/Asuncion",
            "America/La_Paz",
            "America/Lima",
            "Atlantic/Stanley",
            "Africa/Johannesburg",
            "Africa/Maputo",
            "Africa/Harare",
            "Africa/Windhoek",
            "Africa/Gaborone",
            "Africa/Lusaka",
            "Indian/Antananarivo",
            "Indian/Mauritius",
            "Indian/Reunion",
        )
}
