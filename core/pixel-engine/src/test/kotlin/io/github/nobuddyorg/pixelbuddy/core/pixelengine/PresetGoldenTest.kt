package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compares every preset with a reviewed reference image in `src/test/resources/golden`.
 * After an intended change: `./gradlew :core:pixel-engine:test -Dpixelengine.record=true`, then review the PNGs.
 */
class PresetGoldenTest {
    private val record = System.getProperty("pixelengine.record") == "true"

    @ParameterizedTest
    @EnumSource(IconPreset::class)
    fun `preset matches its golden image`(preset: IconPreset) {
        listOf("round" to Fixtures::roundIcon, "quadrants" to Fixtures::quadrants).forEach { (name, fixture) ->
            val output = IconRenderer.render(fixture(preset.style.sourceSize), preset.style, sizePx = 144)
            val golden = File("src/test/resources/golden/${preset.name}_$name.png")

            if (record) {
                Fixtures.writePng(output, golden)
            } else {
                assertTrue(golden.isFile, "missing golden $golden; record with -Dpixelengine.record=true")
                assertEquals(Fixtures.readPng(golden), output, "${preset.name} $name differs from $golden")
            }
        }
    }
}
