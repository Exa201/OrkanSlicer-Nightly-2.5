package ru.ytkab0bp.slicebeam.ui.setup

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ytkab0bp.slicebeam.slic3r.Slic3rConfigWrapper
import java.io.File

class BundledPrinterProfileIntegrityTest {
    @Test
    fun everyVisiblePrinterVariantResolvesToAnInstallablePrinterProfile() {
        val profileDirectory = File("src/main/assets/orca_profiles")
        val iniFiles = profileDirectory.listFiles { file -> file.extension.equals("ini", ignoreCase = true) }
            ?.sortedBy(File::getName)
            .orEmpty()
        assertTrue("Bundled Orca vendor profiles must be present", iniFiles.isNotEmpty())

        iniFiles.forEach { file ->
            val vendor = Slic3rConfigWrapper(file)
            vendor.printerModels.forEach { model ->
                val title = model.title ?: return@forEach
                val variants = SetupProfileSelectionPolicy.printerVariants(
                    model["variants"],
                    model["nozzle_diameter"],
                )
                variants.forEach { variant ->
                    assertNotNull(
                        "${file.name} exposes $title ($variant) but has no matching [printer] profile",
                        vendor.findPrinterVariant(title, variant),
                    )
                }
            }
        }
    }
}
