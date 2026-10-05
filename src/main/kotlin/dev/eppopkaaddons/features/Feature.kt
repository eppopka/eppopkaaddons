package dev.eppopkaaddons.features

import dev.eppopkaaddons.config.ConfigHolder
import dev.eppopkaaddons.config.ConfigManager
import dev.eppopkaaddons.config.SettingProvider
import dev.eppopkaaddons.event.*
import dev.eppopkaaddons.event.priority.EventPriority
import dev.eppopkaaddons.features.annotations.AlwaysActive
import dev.eppopkaaddons.ui.clickgui.enums.CategoryType
import dev.eppopkaaddons.ui.hud.HudElement
import dev.eppopkaaddons.ui.hud.HudProvider
import dev.eppopkaaddons.ui.notification.NotificationManager
import dev.eppopkaaddons.utils.spaceCaps
import net.minecraft.client.gui.GuiGraphicsExtractor

open class Feature(
    val description: String? = null,
    name: String? = null,
    toggled: Boolean = false,
    jsonName: String? = null,
): Shortcuts, SettingProvider, HudProvider {
    val name = name ?: this::class.simpleName.toString().spaceCaps()
    val jsonName = jsonName ?: this.name
    @JvmField var enabled = toggled
    open val category = initCategory()

    private val alwaysActive = this::class.java.isAnnotationPresent(AlwaysActive::class.java)
    override val configSettings = mutableSetOf<ConfigHolder<*>>()
    val listeners = mutableSetOf<EventListener<*>>()
    override val hudElements = mutableSetOf<HudElement>()

    fun initialize() {
        init()

        if (enabled || alwaysActive) onEnable() else onDisable()
    }

    open fun init() = Unit
    open fun onEnable() = listeners.forEach(EventListener<*>::register)
    open fun onDisable() {
        if (alwaysActive) return
        listeners.forEach(EventListener<*>::unregister)
    }

    open fun toggle() {
        enabled = ! enabled
        if (enabled || alwaysActive) onEnable()
        else onDisable()
        if (dev.eppopkaaddons.EppopkaAddons.isLoaded) ConfigManager.markDirty()
    }

    protected inline fun <reified T: Event> register(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline block: EventContext<T>.() -> Unit
    ): EventListener<T> {
        val listener = EventBus.listener<T>(priority, receiveCancelled, block)
        listeners.add(listener)
        return listener
    }

    fun hudElement(
        name: String = this@Feature.jsonName,
        enabled: () -> Boolean = { true },
        shouldDraw: () -> Boolean = { true },
        centered: Boolean = false,
        render: (GuiGraphicsExtractor, Boolean) -> Pair<Number, Number>
    ) = object: HudElement() {
        override val name = name
        override val centered = centered
        override val toggle get() = this@Feature.enabled && enabled.invoke()
        override val shouldDraw get() = shouldDraw.invoke()
        override fun draw(ctx: GuiGraphicsExtractor, example: Boolean) = render(ctx, example)
    }.also(hudElements::add)

    private fun initCategory(): CategoryType {
        val parts = this::class.java.`package` !!.name.split(".")
        val categoryName = parts[parts.indexOf("impl") + 1].uppercase()
        if (CategoryType.entries.none { it.name.equals(categoryName, true) }) error("Category does not exist: $categoryName")
        return CategoryType.valueOf(categoryName.uppercase())
    }
}