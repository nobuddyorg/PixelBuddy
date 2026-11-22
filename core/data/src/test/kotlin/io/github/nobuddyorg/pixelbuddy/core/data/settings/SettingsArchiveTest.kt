package io.github.nobuddyorg.pixelbuddy.core.data.settings

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsArchiveTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val maps = AppKey("org.maps", "org.maps.Main", 0)
    private val bahn = AppKey("org.bahn", "org.bahn.Main", 0)

    private fun pictureStore(): Pair<CustomIconStore, String> {
        val store = CustomIconStore(folder.newFolder())
        val png =
            ByteArrayOutputStream().also {
                createBitmap(80, 80).apply { eraseColor(Color.MAGENTA) }.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        val name = (store.import(ByteArrayInputStream(png.toByteArray())) as CustomIconStore.ImportResult.Imported).name
        return store to name
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                ZipOutputStream(out).use { zip ->
                    entries.forEach { (name, bytes) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
                }
            }.toByteArray()

    private suspend fun read(bytes: ByteArray) = SettingsArchive.read(ByteArrayInputStream(bytes))

    private suspend fun refusal(bytes: ByteArray) = assertIs<SettingsArchive.ReadResult.Refused>(read(bytes)).problem

    @Test
    fun `settings and their pictures survive a round trip to another phone`() =
        runTest {
            val (store, picture) = pictureStore()
            val settings =
                Settings(
                    favorites = listOf(maps, bahn),
                    hidden = setOf(AppKey("org.bloat", "org.bloat.Main", 0)),
                    appearance =
                        Appearance(
                            accent = Accent.Green,
                            sky = Sky.Night,
                            clockFormat = ClockFormat.TwelveHour,
                        ),
                    iconStyle = IconStyleSetting.of(IconPreset.Pocket).edited { it.copy(grid = 32) },
                    gestures = Gestures(swipeUp = GestureAction.Nothing),
                    weather =
                        WeatherSettings(
                            enabled = true,
                            place = WeatherPlace("Berlin", latitude = 52.5, longitude = 13.4),
                        ),
                    setup = SetupStep.Done,
                ).withIconOverride(maps, IconChoice(IconStyleSetting.of(IconPreset.Neon), image = picture))
                    .withIconOverride(bahn, IconChoice(layer = IconLayer.Monochrome))
            val file = ByteArrayOutputStream().also { SettingsArchive.write(settings, store, it) }.toByteArray()

            val archive = assertIs<SettingsArchive.ReadResult.Read>(read(file)).archive
            val newPhone = CustomIconStore(folder.newFolder())
            val restored = SettingsArchive.restore(archive, Settings(setup = SetupStep.Favorites), newPhone)

            assertEquals(settings.copy(setup = SetupStep.Favorites, waitingApps = settings.appKeys), restored)
            assertEquals(80 / 80 * 32, newPhone.load(restored.iconOverrideFor(maps)!!.image!!, 32)!!.width)
        }

    @Test
    fun `a file from before versions were written still reads`() =
        runTest {
            val old = """{"favorites":[{"packageName":"org.maps","activityName":"org.maps.Main","userSerial":0}]}"""

            val archive =
                assertIs<SettingsArchive.ReadResult.Read>(
                    read(
                        zip(SettingsArchive.SETTINGS_ENTRY to old.encodeToByteArray()),
                    ),
                ).archive

            assertEquals(listOf(maps), archive.settings.favorites)
        }

    @Test
    fun `a file from a newer PixelBuddy is refused`() =
        runTest {
            val newer = """{"version":${SettingsSchema.CURRENT_VERSION + 1},"favorites":[]}"""

            assertEquals(
                SettingsArchive.Problem.NewerVersion,
                refusal(
                    zip(SettingsArchive.SETTINGS_ENTRY to newer.encodeToByteArray()),
                ),
            )
        }

    @Test
    fun `corrupt archives and settings are refused`() =
        runTest {
            assertEquals(SettingsArchive.Problem.NotAnArchive, refusal("not a zip at all".encodeToByteArray()))
            assertEquals(SettingsArchive.Problem.NotAnArchive, refusal(ByteArray(0)))
            val truncated = zip(SettingsArchive.SETTINGS_ENTRY to ByteArray(5000) { 'x'.code.toByte() }).copyOf(60)
            assertEquals(SettingsArchive.Problem.NotAnArchive, refusal(truncated))
            assertEquals(SettingsArchive.Problem.NoSettings, refusal(zip("other.txt" to byteArrayOf(1))))
            assertEquals(
                SettingsArchive.Problem.Unreadable,
                refusal(zip(SettingsArchive.SETTINGS_ENTRY to "{not json".encodeToByteArray())),
            )
            assertEquals(
                SettingsArchive.Problem.Unreadable,
                refusal(zip(SettingsArchive.SETTINGS_ENTRY to "[1,2]".encodeToByteArray())),
            )
        }

    @Test
    fun `oversized entries are refused`() =
        runTest {
            val huge = ByteArray(SettingsArchive.MAX_SETTINGS_BYTES + 1) { ' '.code.toByte() }
            assertEquals(SettingsArchive.Problem.TooLarge, refusal(zip(SettingsArchive.SETTINGS_ENTRY to huge)))

            val bigImage = ByteArray(SettingsArchive.MAX_IMAGE_BYTES + 1)
            assertEquals(
                SettingsArchive.Problem.TooLarge,
                refusal(zip("icon-images/${"a".repeat(64)}.png" to bigImage)),
            )

            val many = (0..SettingsArchive.MAX_ENTRIES).map { "junk/$it" to byteArrayOf() }.toTypedArray()
            assertEquals(SettingsArchive.Problem.TooLarge, refusal(zip(*many)))
        }

    @Test
    fun `entry names that try to leave the archive refuse it`() =
        runTest {
            val settings = SettingsArchive.SETTINGS_ENTRY to "{}".encodeToByteArray()
            listOf("../evil.png", "icon-images/../../x", "/etc/passwd", "a\\..\\b", "C:/x", "./x").forEach { name ->
                assertEquals(SettingsArchive.Problem.UnsafeEntry, refusal(zip(settings, name to byteArrayOf(1))), name)
            }
        }

    @Test
    fun `unknown entries are skipped and pictures are checked again`() =
        runTest {
            val (store, picture) = pictureStore()
            val broken = "b".repeat(64)
            val settings =
                Settings()
                    .withIconOverride(maps, IconChoice(image = picture))
                    .withIconOverride(bahn, IconChoice(image = broken))
            val file =
                zip(
                    SettingsArchive.SETTINGS_ENTRY to
                        SettingsSerializer.json.encodeToString(Settings.serializer(), settings).encodeToByteArray(),
                    "icon-images/$picture.png" to store.bytes(picture)!!,
                    "icon-images/$broken.png" to "not a png".encodeToByteArray(),
                    "icon-images/not-a-hash.png" to byteArrayOf(1),
                    "from-the-future.bin" to byteArrayOf(1, 2, 3),
                )

            val archive = assertIs<SettingsArchive.ReadResult.Read>(read(file)).archive
            assertEquals(setOf(picture, broken), archive.images.keys)
            val restored = SettingsArchive.restore(archive, Settings(), CustomIconStore(folder.newFolder()))

            assertEquals(picture, restored.iconOverrideFor(maps)!!.image)
            // A picture that doesn't decode falls back to the app's icon.
            assertNull(restored.iconOverrideFor(bahn)!!.image)
        }
}
