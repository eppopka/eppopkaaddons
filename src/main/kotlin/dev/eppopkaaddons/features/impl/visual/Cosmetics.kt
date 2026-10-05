package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.EppopkaAddons
import dev.eppopkaaddons.config.types.ButtonSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.impl.visual.cosmetics.CosmeticData
import dev.eppopkaaddons.features.impl.visual.cosmetics.badges.BadgeManager
import dev.eppopkaaddons.features.impl.visual.cosmetics.badges.BadgeText
import dev.eppopkaaddons.features.impl.visual.cosmetics.text.TextReplacer
import dev.eppopkaaddons.ui.notification.NotificationManager
import dev.eppopkaaddons.utils.ChatUtils
import dev.eppopkaaddons.utils.MathUtils.vec
import dev.eppopkaaddons.utils.NumbersUtils
import dev.eppopkaaddons.utils.network.ProfileUtils
import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.coroutines.*
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.world.entity.Avatar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Cosmetics : Feature(
    description = "Owner-maintained custom player names and cosmetics.",
    name = "Custom Names",
    toggled = true,
) {
    @JvmStatic val customNames by ToggleSetting("Show Custom Names", true)
    @JvmStatic val showBadges by ToggleSetting("Show Custom Badges", true)
    val reload by ButtonSetting("Reload Custom Names") {
        if (System.currentTimeMillis() - lastReload >= 15_000) init()
        else NotificationManager.push("Custom Names", "Please wait ${NumbersUtils.formatTime(15_000 - (System.currentTimeMillis() - lastReload))} before reloading.")
    }

    private var cosmeticPeople: Map<UUID, CosmeticData> = emptyMap()
    private val profileNames = ConcurrentHashMap<UUID, String>()
    private var lastReload = 0L

    override fun init() {
        scope.launch(Dispatchers.IO) {
            lastReload = System.currentTimeMillis()
            TextReplacer.ready = false
            BadgeManager.load().onSuccess { people ->
                cosmeticPeople = people
                BadgeText.init(people)
                val replacements = coroutineScope {
                    people.mapNotNull { (uuid, cosmetic) ->
                        async {
                            val original = profileNames[uuid]
                                ?: ProfileUtils.getNameByUUID(uuid).getOrNull()?.name
                                ?: return@async null
                            profileNames[uuid] = original
                            original to cosmetic.name
                        }
                    }.awaitAll().filterNotNull().toMap()
                }
                TextReplacer.init(replacements)
                EppopkaAddons.logger.info("Loaded ${people.size} allow-listed custom names")
            }.onFailure { cause ->
                TextReplacer.init(emptyMap())
                EppopkaAddons.logger.error("Failed to load custom names from ${BadgeManager.URL}", cause)
                ChatUtils.modMessage("&cFailed to load custom names: ${cause.message}")
            }
        }
    }

    @JvmStatic
    fun extractRenderStateHook(avatar: Avatar, state: AvatarRenderState) {
        if (!enabled || avatar !is AbstractClientPlayer) return
        state.setData(GAME_PROFILE_KEY, avatar.gameProfile)
        state.setData(SNEAKING_KEY, avatar.isCrouching)
    }

    @JvmStatic
    fun scaleHook(state: AvatarRenderState, poseStack: PoseStack) {
        // Custom size, halo and wings controls were removed. Keep this hook as
        // a no-op so existing mixins remain binary-compatible.
    }

    fun cosmeticDataFor(uuid: UUID) = cosmeticPeople[uuid]
    val GAME_PROFILE_KEY = RenderStateDataKey.create<GameProfile> { "${EppopkaAddons.MOD_ID}:game_profile" }
    val SNEAKING_KEY = RenderStateDataKey.create<Boolean> { "${EppopkaAddons.MOD_ID}:sneaking" }
}
