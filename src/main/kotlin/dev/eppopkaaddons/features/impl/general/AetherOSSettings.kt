package dev.eppopkaaddons.features.impl.general

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.features.Feature
import java.awt.Color

data class InterfacePalette(
    val background: Color,
    val surface: Color,
    val elevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val accent: Color,
)

/** Settings exposed by the GUI Settings button. Every visible option is consumed by /ea. */
object AetherOSSettings : Feature(
    description = "Appearance, background, accessibility, and motion settings for /ea.",
    name = "Interface",
    toggled = true,
    jsonName = "AetherOS",
) {
    val theme by DropdownSetting("Theme", 0, listOf("Midnight", "Graphite", "Arctic", "Rose", "Forest", "Custom"))
        .section("Theme")
        .withDescription("Changes the complete interface palette, not only the accent color.")
    val customAccent by ColorSetting("Custom Accent", Color(116, 132, 255), false).showIf { theme.value == 5 }
    val customBackground by ColorSetting("Custom Background", Color(8, 11, 17), false).showIf { theme.value == 5 }
    val customSurface by ColorSetting("Custom Surface", Color(13, 18, 27), false).showIf { theme.value == 5 }
    val customElevated by ColorSetting("Custom Elevated Surface", Color(21, 27, 39), false).showIf { theme.value == 5 }
    val customTextPrimary by ColorSetting("Custom Primary Text", Color(242, 245, 251), false).showIf { theme.value == 5 }
    val customTextSecondary by ColorSetting("Custom Secondary Text", Color(155, 165, 183), false).showIf { theme.value == 5 }
    val customBorder by ColorSetting("Custom Border", Color(105, 119, 145), false).showIf { theme.value == 5 }
    val glassStrength by SliderSetting("Surface Opacity", .92f, .15f, 1f, .01f)
        .withDescription("Controls the opacity of every ClickGUI panel and control.")

    val showBackground by ToggleSetting("Dim World", true).section("Background")
        .withDescription("Enables the selected world backdrop treatment.")
    val backgroundStyle by DropdownSetting("Background Style", 0, listOf("Gradient", "Solid", "Grid", "Transparent"))
        .showIf { showBackground.value }
        .withDescription("Selects the actual backdrop renderer used behind /ea.")
    val backgroundOpacity by SliderSetting("World Dim", .5f, 0f, 1f, .01f)
        .showIf { showBackground.value && backgroundStyle.value != 3 }
    val backgroundBlur by ToggleSetting("Background Blur", true)
        .showIf { showBackground.value }
        .withDescription("Applies Minecraft's native world blur behind the interface.")

    val animations by ToggleSetting("Animations", true).section("Motion")
        .withDescription("Enables spatial unfolding and spring transitions.")
    val animationSpeed by SliderSetting("Animation Speed", 1f, .25f, 2.5f, .05f)
        .showIf { animations.value }
    val clickRipples by ToggleSetting("Click Feedback", true)
        .showIf { animations.value }
        .withDescription("Shows the circular BIOS shockwave after an action.")
    val reducedMotion by ToggleSetting("Reduced Motion", false)
        .withDescription("Disables decorative movement while keeping state changes immediate.")

    val currentPalette: InterfacePalette
        get() = when (theme.value) {
            1 -> InterfacePalette(Color(8, 8, 9), Color(18, 18, 20), Color(29, 29, 33), Color(247, 247, 249), Color(165, 165, 174), Color(100, 100, 112), Color(167, 139, 250))
            2 -> InterfacePalette(Color(4, 12, 17), Color(9, 24, 32), Color(14, 37, 48), Color(237, 249, 253), Color(146, 180, 193), Color(77, 139, 165), Color(86, 203, 255))
            3 -> InterfacePalette(Color(16, 7, 13), Color(31, 15, 25), Color(48, 22, 38), Color(255, 241, 249), Color(202, 157, 184), Color(154, 73, 119), Color(255, 105, 171))
            4 -> InterfacePalette(Color(4, 12, 9), Color(10, 25, 19), Color(16, 39, 29), Color(238, 251, 245), Color(146, 185, 168), Color(63, 132, 101), Color(77, 218, 156))
            5 -> InterfacePalette(customBackground.value, customSurface.value, customElevated.value, customTextPrimary.value, customTextSecondary.value, customBorder.value, customAccent.value)
            else -> InterfacePalette(Color(3, 6, 11), Color(7, 12, 20), Color(14, 21, 34), Color(242, 245, 251), Color(155, 165, 183), Color(81, 94, 122), Color(124, 113, 255))
        }

    val currentAccent get() = currentPalette.accent

    fun background(alpha: Int) = currentPalette.background.withOpacity(alpha)
    fun surface(alpha: Int, elevated: Boolean = false): Color =
        (if (elevated) currentPalette.elevated else currentPalette.surface)
            .withOpacity((alpha * glassStrength.value).toInt())
    fun border(alpha: Int) = currentPalette.border.withOpacity(alpha)
    fun primary(alpha: Int = 255) = currentPalette.textPrimary.withOpacity(alpha)
    fun secondary(alpha: Int = 255) = currentPalette.textSecondary.withOpacity(alpha)

    private fun Color.withOpacity(alpha: Int) = Color(red, green, blue, alpha.coerceIn(0, 255))

    override fun toggle() = Unit
}
