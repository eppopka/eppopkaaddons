import java.io.File
import kotlin.test.*

class HotbarRequestedChangesTest {
    @Test fun removedSettingsAreNotRegistered() {
        val source = File("src/main/kotlin/dev/eppopkaaddons/features/impl/visual/MoveHotbar.kt").readText()
        assertFalse(source.contains("SliderSetting(\"Scale\""))
        assertFalse(source.contains("SliderSetting(\"Slot Size\""))
        assertTrue(source.contains("override val name = \"Held Item Name\""))
        assertTrue(source.contains("listOf(\"Follow Hotbar\", \"Independent\")"))
    }
    @Test fun hudCommandOpensTheEditorAndDungeonCommandRemains() {
        val source = File("src/main/kotlin/dev/eppopkaaddons/commands/NaCommand.kt").readText()
        assertTrue(source.contains("literal(\"hud\")")); assertTrue(source.contains("GuiUtils.setScreen(HudEditorScreen())"))
        assertTrue(source.contains("literal(\"dwp\")"))
    }
}
