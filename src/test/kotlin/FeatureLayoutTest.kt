import java.io.File
import kotlin.test.*

class FeatureLayoutTest {
    private val impl = File("src/main/kotlin/dev/eppopkaaddons/features/impl")
    @Test fun movedFeaturesHaveExactlyOneSource() {
        val files = impl.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertEquals(listOf("general/PetDisplay.kt"), files.filter { it.name == "PetDisplay.kt" }.map { it.relativeTo(impl).invariantSeparatorsPath })
        assertEquals(listOf("dungeon/ShitterList.kt"), files.filter { it.name == "ShitterList.kt" }.map { it.relativeTo(impl).invariantSeparatorsPath })
        assertFalse(files.any { it.name == "LbTimer.kt" })
    }
    @Test fun featurePackagesMatchTheirCategoryFolders() {
        impl.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            val folder = file.parentFile.relativeTo(impl).invariantSeparatorsPath.replace('/', '.')
            val declared = Regex("(?m)^package\\s+(\\S+)").find(file.readText())?.groupValues?.get(1)
            assertEquals("dev.eppopkaaddons.features.impl.$folder", declared, file.path)
        }
        assertFalse(File(impl, "general/PetDisplay.kt").readText().contains("override val category"))
    }
    @Test fun spatialGuiUsesFolderDerivedCategories() {
        val gui = File("src/main/kotlin/dev/eppopkaaddons/ui/clickgui/enums/SpatialCategory.kt").readText()
        assertTrue(gui.contains(".filter { it.category == featureCategory }")); assertFalse(gui.contains("assignments"))
    }
    @Test fun petPortraitUsesNativeLocalRenderingAndXpDoesNotUseSkillActionBars() {
        val view = File("src/main/kotlin/dev/eppopkaaddons/ui/hud/pet/PetOverlayView.kt").readText()
        val pet = File(impl, "general/PetDisplay.kt").readText()
        assertTrue(view.contains("ctx.pose().translate(17f, 17f)"))
        assertTrue(view.contains("ctx.item(stack, 0, 0)"))
        assertFalse(pet.contains("register<ActionBarMessageEvent>")); assertFalse(pet.contains("drawBatchedItemStack"))
    }
}
