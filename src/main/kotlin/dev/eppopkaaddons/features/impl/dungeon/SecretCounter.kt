package dev.eppopkaaddons.features.impl.dungeon

import dev.eppopkaaddons.EppopkaAddons.mc
import dev.eppopkaaddons.config.types.DropdownSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import dev.eppopkaaddons.event.impl.ChatMessageEvent
import dev.eppopkaaddons.event.impl.DungeonEvent
import dev.eppopkaaddons.event.impl.TickEvent
import dev.eppopkaaddons.event.impl.WorldChangeEvent
import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.utils.ChatUtils
import dev.eppopkaaddons.utils.location.LocationUtils
import gg.essential.universal.UChat

object SecretCounter: Feature(
    "Automatically runs /showextrastats after a dungeon and reports your personal Secrets Found.",
    name = "Secret Counter"
) {
    private val autoAnnounce by ToggleSetting("Auto Announce", true)
        .withDescription("Sends [EA] I did {secrets} secrets! once your personal extra stats arrive.")
    private val announceChannel by DropdownSetting("Announce Channel", 0, listOf("Current Chat", "Party Chat"))
        .withDescription("Current Chat follows your selected chat channel. Party Chat always uses /pc.")
        .showIf { autoAnnounce.value }

    private val state = SecretCounterState()
    private var announcement: Pair<Long, Int>? = null

    override fun init() {
        register<WorldChangeEvent> { reset() }
        register<DungeonEvent.RunStatedEvent> { mc.execute { reset() } }
        // DungeonListener posts this event from a coroutine. Confine state to the client thread.
        register<DungeonEvent.RunEndedEvent> {
            mc.execute {
                if (enabled && LocationUtils.inDungeon) state.finish(System.currentTimeMillis())
            }
        }
        register<ChatMessageEvent>(receiveCancelled = true) {
            if (! LocationUtils.inDungeon) return@register
            val now = System.currentTimeMillis()
            val secrets = state.readSecrets(event.unformattedText, now) ?: return@register
            ChatUtils.modMessage("§bSecrets Found: §a$secrets")
            if (autoAnnounce.value) announcement = (now + 500) to secrets
        }
        register<TickEvent.End> {
            if (! LocationUtils.inDungeon || mc.player == null) {
                reset()
                return@register
            }
            val now = System.currentTimeMillis()
            when (state.poll(now)) {
                SecretCounterState.Action.REQUEST_STATS -> mc.player?.connection?.sendCommand("showextrastats")
                SecretCounterState.Action.TIMEOUT ->
                    ChatUtils.modMessage("§eSecret Counter: no Secrets Found result received; announcement skipped.")
                null -> Unit
            }
            announcement?.let { (due, count) ->
                if (now < due) return@let
                announcement = null
                if (! autoAnnounce.value) return@let
                val message = "[EA] I did $count secrets!"
                if (announceChannel.value == 1) mc.player?.connection?.sendCommand("pc $message")
                else UChat.say(message)
            }
        }
    }

    override fun onEnable() {
        reset()
        super.onEnable()
    }

    override fun onDisable() {
        reset()
        super.onDisable()
    }

    private fun reset() {
        state.reset()
        announcement = null
    }
}
/** One request and one personal secret result per run. No network/API estimates. */
private class SecretCounterState {
    enum class Action { REQUEST_STATS, TIMEOUT }

    private var finished = false
    private var requestAt: Long? = null
    private var expiresAt: Long? = null
    private val secretsRegex = Regex("""^\s*Secrets Found:\s*([0-9]+(?:,[0-9]{3})*)\s*$""")

    fun finish(now: Long) {
        if (finished) return
        finished = true
        // Let the server finish printing the run summary first.
        requestAt = now + 1_000
    }

    fun poll(now: Long): Action? {
        requestAt?.let {
            if (now >= it) {
                requestAt = null
                expiresAt = now + 10_000
                return Action.REQUEST_STATS
            }
        }
        expiresAt?.let {
            if (now >= it) {
                expiresAt = null
                return Action.TIMEOUT
            }
        }
        return null
    }

    fun readSecrets(text: String, now: Long): Int? {
        val deadline = expiresAt ?: return null
        if (now >= deadline) return null
        // Match a complete server stat line, never a player/party message or team total.
        val match = secretsRegex.matchEntire(text) ?: return null
        val count = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        expiresAt = null
        return count
    }

    fun reset() {
        finished = false
        requestAt = null
        expiresAt = null
    }
}