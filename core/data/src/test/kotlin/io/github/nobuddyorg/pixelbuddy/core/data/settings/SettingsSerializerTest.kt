package io.github.nobuddyorg.pixelbuddy.core.data.settings

import androidx.datastore.core.CorruptionException
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.Palette
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsSerializerTest {
    private val maps = AppKey("com.google.android.apps.maps", "com.google.Maps", 0)
    private val teams = AppKey("com.microsoft.teams", "com.microsoft.Teams", 10)

    private suspend fun SettingsSerializer.read(json: String): Settings = readFrom(json.byteInputStream())

    private suspend fun SettingsSerializer.write(settings: Settings): String =
        ByteArrayOutputStream().also { writeTo(settings, it) }.toByteArray().decodeToString()

    @Test
    fun `settings survive a round trip`() =
        runTest {
            val settings =
                Settings(
                    favorites = listOf(teams, maps),
                    hidden = setOf(AppKey("com.example.bloat", "com.example.Main", 0)),
                    appearance = Appearance(ClockFormat.TwentyFourHour),
                    iconStyle = IconStyleSetting.of(IconPreset.Pocket),
                    iconOverrides = listOf(IconOverride(maps, IconStyleSetting.of(IconPreset.Mono))),
                    weather =
                        WeatherSettings(
                            enabled = true,
                            unit = TemperatureUnit.Fahrenheit,
                            place = WeatherPlace("Berlin", "Land Berlin", "Deutschland", 52.52437, 13.41053),
                            approximateLocation = true,
                            tapApp = teams,
                        ),
                )
            val serializer = SettingsSerializer()

            assertEquals(settings, serializer.read(serializer.write(settings)))
        }

    @Test
    fun `the file is versioned JSON`() =
        runTest {
            val json = SettingsSerializer().write(Settings(favorites = listOf(maps)))

            assertTrue(
                json.startsWith("""{"version":1,"favorites":[{"packageName":"com.google.android.apps.maps""""),
                json,
            )
        }

    @Test
    fun `weather is off by default`() {
        assertEquals(false, Settings().weather.enabled)
    }

    @Test
    fun `missing properties get their defaults`() =
        runTest {
            val settings =
                SettingsSerializer().read(
                    """{"version":1,"favorites":[{"packageName":"a","activityName":"a.Main","userSerial":0}]}""",
                )

            assertEquals(Settings(favorites = listOf(AppKey("a", "a.Main", 0))), settings)
        }

    @Test
    fun `values from a newer version don't break reading`() =
        runTest {
            val settings =
                SettingsSerializer().read(
                    """{"version":7,"widgets":[1,2],"appearance":{"clockFormat":"Binary"},"iconStyle":{"preset":"Hologram"}}""",
                )

            assertEquals(ClockFormat.System, settings.appearance.clockFormat)
            assertEquals(IconPreset.Default.style, settings.iconStyle.style)
            assertEquals(SettingsSchema.CURRENT_VERSION, settings.version)
        }

    @Test
    fun `unknown appearance and gesture values fall back to the defaults`() =
        runTest {
            val settings =
                SettingsSerializer().read(
                    """{"version":1,"appearance":{"accent":"Ultraviolet","sky":"Mars","clockSize":"Huge",""" +
                        """"scanlines":true},"gestures":{"swipeUp":"Teleport","longPress":"Search"}}""",
                )

            assertEquals(Appearance(scanlines = true), settings.appearance)
            assertEquals(Gestures(longPress = GestureAction.Search), settings.gestures)
        }

    @Test
    fun `every preset survives the editor unchanged`() {
        IconPreset.entries.forEach { preset ->
            assertEquals(preset.style, CustomIconStyle.from(preset.style).toStyle(), preset.name)
        }
    }

    @Test
    fun `editing a preset makes a custom style based on it`() {
        val edited = IconStyleSetting.of(IconPreset.Neon).edited { it.copy(grid = 32) }

        assertEquals(IconPreset.Neon, edited.basePreset)
        assertEquals(IconPreset.Neon.style.copy(grid = 32), edited.style)
        assertEquals(edited.style.copy(dithering = true), edited.edited { it.copy(dithering = true) }.style)
    }

    @Test
    fun `damaged custom values are brought into range`() =
        runTest {
            val settings =
                SettingsSerializer().read(
                    """{"version":1,"iconStyle":{"preset":"Pocket","custom":{"grid":7,"palette":"Adaptive",""" +
                        """"colors":900,"intensity":4.5,"outlineColor":0,"masked":false}}}""",
                )

            val style = settings.iconStyle.style
            assertEquals(24, style.grid)
            assertEquals(Palette.Adaptive(32), style.palette)
            assertEquals(1f, style.intensity)
            assertEquals(0xFF000000.toInt(), style.outlineColor)
            assertEquals(IconShape.FreeStanding, style.shape)
            assertEquals(Palette.MonochromeTint(0xFFF2EEFF.toInt(), 2), PaletteChoice.Mono.toPalette(1))
            assertEquals(Palette.Original, PaletteChoice.Original.toPalette(5))
        }

    @Test
    fun `icon choices survive a JSON round trip, other text gives null`() {
        val choice =
            IconChoice(
                IconStyleSetting.of(IconPreset.Mono).edited { it.copy(colors = 6) },
                IconLayer.Foreground,
                "f".repeat(64),
            )

        assertEquals(choice, SettingsSerializer.decodeIconChoice(SettingsSerializer.encode(choice)))
        assertEquals(null, SettingsSerializer.decodeIconChoice("not json"))
    }

    @Test
    fun `older files are upgraded step by step`() =
        runTest {
            // A made-up history: v1 called the favorites "pinned", v2 added a flag that v3 dropped again.
            val steps =
                mapOf(
                    1 to SchemaStep { json -> JsonObject(json - "pinned" + ("favorites" to json.getValue("pinned"))) },
                    2 to SchemaStep { json -> JsonObject(json + ("legacy" to JsonPrimitive(true))) },
                )
            val serializer = SettingsSerializer(steps, currentVersion = 3)

            val settings =
                serializer.read(
                    """{"version":1,"pinned":[{"packageName":"a","activityName":"a.Main","userSerial":0}]}""",
                )

            assertEquals(listOf(AppKey("a", "a.Main", 0)), settings.favorites)
            assertEquals(3, settings.version)
            assertTrue(serializer.write(settings).startsWith("""{"version":3,"""))
        }

    @Test
    fun `a file without a version counts as the first version`() =
        runTest {
            val steps = mapOf(1 to SchemaStep { JsonObject(it + ("hidden" to JsonPrimitive("bad"))) })

            assertFailsWith<CorruptionException> { SettingsSerializer(steps, currentVersion = 2).read("{}") }
        }

    @Test
    fun `a missing upgrade step is corruption`() =
        runTest {
            assertFailsWith<CorruptionException> {
                SettingsSerializer(
                    emptyMap(),
                    currentVersion = 2,
                ).read("""{"version":1}""")
            }
        }

    @Test
    fun `unreadable files are corruption`() =
        runTest {
            listOf("", "{", "[]", "null", """{"version":"one"}""", """{"favorites":"Maps"}""").forEach { broken ->
                assertFailsWith<CorruptionException>(broken) { SettingsSerializer().read(broken) }
            }
        }
}
