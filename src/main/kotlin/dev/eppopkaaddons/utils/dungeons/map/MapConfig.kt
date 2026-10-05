package dev.eppopkaaddons.utils.dungeons.map

import dev.eppopkaaddons.config.types.ColorSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import java.awt.Color

/** Scanner-only palette retained for room transforms used by Odin waypoints. */
object MapConfig {
    val dungeonMapCheater = ToggleSetting("Scanner Mode", false)
    val printPlayersClearInfo = ToggleSetting("Player Clear Info", false)
    val centerStyle = ToggleSetting("Center Room Text", true)

    val colorUnopened = ColorSetting("Unopened Room", Color(65, 65, 65), true)
    val colorBlood = ColorSetting("Blood Room", Color(178, 0, 0), true)
    val colorFairy = ColorSetting("Fairy Room", Color(227, 155, 226), true)
    val colorRare = ColorSetting("Rare Room", Color(178, 178, 178), true)
    val colorMiniboss = ColorSetting("Miniboss Room", Color(255, 200, 0), true)
    val colorPuzzle = ColorSetting("Puzzle Room", Color(123, 0, 123), true)
    val colorTrap = ColorSetting("Trap Room", Color(255, 130, 0), true)
    val colorRoom = ColorSetting("Normal Room", Color(121, 70, 0), true)
    val colorEntrance = ColorSetting("Entrance Room", Color(0, 255, 0), true)
    val colorUnopenedDoor = ColorSetting("Unopened Door", colorUnopened.value, true)
    val colorBloodDoor = ColorSetting("Blood Door", colorBlood.value, true)
    val colorWitherDoor = ColorSetting("Wither Door", Color(16, 16, 16), true)
    val colorRoomDoor = ColorSetting("Normal Door", colorRoom.value, true)
    val colorOpenWitherDoor = ColorSetting("Opened Wither Door", colorRoom.value, true)
    val colorEntranceDoor = ColorSetting("Entrance Door", colorEntrance.value, true)
}
