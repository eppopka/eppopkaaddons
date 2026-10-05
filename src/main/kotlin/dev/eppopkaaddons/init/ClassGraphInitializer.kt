@file:Suppress("UNCHECKED_CAST")

package dev.eppopkaaddons.init

import dev.eppopkaaddons.EppopkaAddons
import dev.eppopkaaddons.commands.CommandBuilder
import dev.eppopkaaddons.config.ConfigManager
import dev.eppopkaaddons.event.EventBus.register
import dev.eppopkaaddons.event.impl.RenderOverlayEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.features.FeatureManager
import dev.eppopkaaddons.init.types.ICommandProvider
import dev.eppopkaaddons.init.types.ICustomMenu
import dev.eppopkaaddons.init.types.ISelfInit
import dev.eppopkaaddons.ui.hud.HudEditorScreen
import dev.eppopkaaddons.ui.utils.Resolution
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import gg.essential.universal.UMinecraft
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.ScanResult
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import kotlin.system.measureTimeMillis

class ClassGraphInitializer {
    fun initAll() = ClassGraph().enableAllInfo().apply {
        acceptPackages(EppopkaAddons::class.java.`package`.name)
        overrideClassLoaders(Thread.currentThread().contextClassLoader)
    }.scan().use { scan ->
        val time = measureTimeMillis {
            scan.registerSelfInits()
            scan.registerCustomMenus()
            scan.registerCommands()
            scan.registerFeatures()
        }

        EppopkaAddons.logger.info("${this::class.simpleName} initialized in $time ms.")
    }

    private fun ScanResult.registerSelfInits() {
        getClassesImplementing(ISelfInit::class.java)
            .filter {
                it.name == "dev.eppopkaaddons.event.EventDispatcher" ||
                    it.name == "dev.eppopkaaddons.ui.notification.NotificationManager" ||
                    it.name.startsWith("dev.eppopkaaddons.utils.")
            }
            .filterNot {
                it.name.startsWith("dev.eppopkaaddons.utils.network.")
            }
            .forEach { ci ->
            (ci.getInstance<ISelfInit>()).init()
        }
    }

    private fun ScanResult.registerCustomMenus() {
        getClassesImplementing(ICustomMenu::class.java)
            .filter { it.name.startsWith("dev.eppopkaaddons.features.impl.") }
            .forEach { ci ->
            ModCompatibility.customMenus.add(ci.getInstance())
        }
    }

    private fun ScanResult.registerCommands() {
        val allowedCommands = setOf(
            "dev.eppopkaaddons.commands.NaCommand"
        )
        val providers = getClassesImplementing(ICommandProvider::class.java)
            .filter { it.name in allowedCommands }
            .map { it.getInstance<ICommandProvider>() }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            providers.forEach { provider ->
                val commandBuilder = CommandBuilder()
                with(provider) { commandBuilder.command() }

                commandBuilder.build().forEach { root ->
                    dispatcher.register(root as LiteralArgumentBuilder<FabricClientCommandSource>)
                }
            }
        }
    }

    private fun ScanResult.registerFeatures() {
        getSubclasses(Feature::class.java)
            .filter { it.name.startsWith("dev.eppopkaaddons.features.impl.") }
            .forEach { classInfo ->
            val feature = classInfo.getInstance<Feature>().apply(Feature::initialize)
            FeatureManager.hudElements.addAll(feature.hudElements)
            FeatureManager.features.add(feature)
        }

        ConfigManager.load()

        register<RenderOverlayEvent> {
            if (UMinecraft.currentScreenObj is HudEditorScreen) return@register

            Resolution.push(event.context)
            FeatureManager.hudElements.forEach { if (it.shouldDraw) it.renderElement(event.context, false) }
            Resolution.pop(event.context)
        }
    }

    private fun <T> ClassInfo.getInstance() = loadClass().getDeclaredField("INSTANCE").get(null) as T
}