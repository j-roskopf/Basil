package com.joetr.basil.updates

import com.joetr.basil.updates.UpdatePlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateAssetSelectionTest {
    @Test
    fun selectsFlatpakAssetForFlatpakInstalls() {
        val asset = selectUpdateAsset(
            UpdatePlatform.LinuxFlatpak,
            listOf(
                GitHubAssetDto("Basil-1.2.3.deb", "https://example.test/basil.deb", state = "uploaded"),
                GitHubAssetDto("Basil-1.2.3.flatpak", "https://example.test/basil.flatpak", state = "uploaded"),
            ),
        )

        assertEquals("Basil-1.2.3.flatpak", asset?.name)
    }

    @Test
    fun flatpakInstallerEscapesToHostAndRelaunchesFlatpak() {
        val script = linuxInstallerHelperScript(
            filePath = "/home/ada/Downloads/Basil-1.2.3.flatpak",
            flatpak = true,
            insideFlatpak = true,
            relaunchCommand = "flatpak run com.joetr.basil",
        )

        assertTrue(script.contains("flatpak-spawn --host sh -c"))
        assertTrue(script.contains("flatpak install --user -y"))
        assertTrue(script.contains("flatpak run com.joetr.basil"))
    }

    @Test
    fun macDmgInstallerReplacesAndRelaunchesAppBundle() {
        val script = macDmgInstallerHelperScript(
            dmgPath = "/tmp/Basil-1.2.3.dmg",
            targetAppBundle = "/Applications/Basil.app",
            parentProcessId = 1234,
        )

        assertTrue(script.contains("/bin/mkdir -p \"${'$'}mount_point\""))
        assertTrue(script.contains("/usr/bin/hdiutil attach \"${'$'}dmg_path\""))
        assertTrue(script.contains("/usr/bin/open \"${'$'}target_app\""))
    }
}
