package dev.eppopkaaddons.features.impl.dungeon

import dev.eppopkaaddons.features.Feature
import dev.eppopkaaddons.config.types.SliderSetting
import dev.eppopkaaddons.config.types.ToggleSetting
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument
import net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * ShitterList - auto-kicks blacklisted players joining your dungeon group via Party Finder.
 *
 * Original mod by qwertycry: https://github.com/qwertycry/Shitterlist (MIT License).
 * Ported into EppopkaAddons. Ban list stays in config/shitters.txt - fully compatible
 * with the original mod's format.
 */
object ShitterList: Feature("Auto-kicks blacklisted players from Party Finder groups. Original mod by qwertycry (github.com/qwertycry/Shitterlist).", name = "Shitter List") {
    private val partyWarning by ToggleSetting("Party Warning", true)
        .withDescription("Sends a /pc warning message before kicking.")

    private val kickDelay by SliderSetting("Kick Delay (ms)", 1100, 100, 3000, 100)
        .withDescription("Delay between the Party Finder join message and the /p kick.")

    // ==================== Data ====================

    data class BanEntry(val uuid: String, val nickname: String = "Unknown", val reason: String = "1")

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dataFile: File = FabricLoader.getInstance().configDir.resolve("shitters.txt").toFile()
    private val entries = mutableListOf<BanEntry>()

    private fun normalise(uuid: String) = uuid.replace("-", "").lowercase().trim()

    private fun load() {
        if (! dataFile.exists()) return save()
        runCatching {
            val json = dataFile.readText()
            if (json.isBlank()) return save()
            entries.clear()
            val seen = mutableSetOf<String>()
            for (element in JsonParser.parseString(json).asJsonArray) runCatching {
                val obj = element.asJsonObject
                val uuid = obj.get("uuid")?.asString ?: return@runCatching
                val n = normalise(uuid)
                if (n.isEmpty() || ! seen.add(n)) return@runCatching
                entries.add(BanEntry(n, obj.get("nickname")?.asString ?: "Unknown", obj.get("reason")?.asString ?: "1"))
            }
        }.onFailure { entries.clear(); save() }
    }

    private fun save() = runCatching {
        dataFile.parentFile?.mkdirs()
        dataFile.writeText(gson.toJson(entries))
    }.let {}

    private fun getEntry(uuid: String) = entries.find { normalise(it.uuid) == normalise(uuid) }

    private fun updateEntry(uuid: String, nickname: String, reason: String) {
        val n = normalise(uuid)
        val idx = entries.indexOfFirst { normalise(it.uuid) == n }
        if (idx != - 1) entries[idx] = BanEntry(n, nickname, reason) else entries.add(BanEntry(n, nickname, reason))
        save()
    }

    // ==================== Mojang API ====================

    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7)).build()

    private fun httpGet(url: String): String? = runCatching {
        val req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(7)).GET().build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() == 200) res.body() else null
    }.getOrNull()

    private fun fetchProfile(username: String): Pair<String, String>? {
        if (username.isBlank()) return null
        val body = httpGet("https://api.mojang.com/users/profiles/minecraft/${username.trim()}") ?: return null
        return runCatching {
            val json = JsonParser.parseString(body).asJsonObject
            val uuid = json.get("id")?.asString?.lowercase() ?: return null
            uuid to (json.get("name")?.asString ?: username.trim())
        }.getOrNull()
    }

    private fun fetchNameByUuid(uuid: String): String? {
        val clean = normalise(uuid)
        if (clean.length != 32) return null
        val formatted = "${clean.substring(0, 8)}-${clean.substring(8, 12)}-${clean.substring(12, 16)}-${clean.substring(16, 20)}-${clean.substring(20)}"
        val body = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/$formatted") ?: return null
        return runCatching { JsonParser.parseString(body).asJsonObject.get("name")?.asString }.getOrNull()
    }

    // ==================== Party Finder listener ====================

    private val STRIP_PATTERN: Pattern = Pattern.compile("\u00a7\\w")
    private const val PF_PREFIX = "Party Finder > "
    private const val PF_SUFFIX = " joined the dungeon group!"
    private val kickCooldowns = ConcurrentHashMap<String, Long>()
    private const val COOLDOWN_MS = 5000L
    private val NICK_REGEX = Regex("[A-Za-z0-9_]{1,16}")

    private fun localMsg(msg: String) = mc.execute { mc.player?.sendSystemMessage(Component.literal(msg)) }

    private fun handleMessage(unformatted: String) {
        if (! unformatted.contains(PF_PREFIX)) return
        val afterPrefix = unformatted.substringAfter(PF_PREFIX)
        if (! afterPrefix.contains(PF_SUFFIX)) return
        val nickname = afterPrefix.substringBefore(PF_SUFFIX).trim()
        if (nickname.isEmpty() || ! nickname.matches(NICK_REGEX)) return

        Thread {
            runCatching { processNickname(nickname) }
        }.also { it.isDaemon = true; it.name = "sl-check" }.start()
    }

    private fun processNickname(nickname: String) {
        val (uuid, name) = fetchProfile(nickname) ?: return
        val entry = getEntry(uuid) ?: return

        val now = System.currentTimeMillis()
        if (now - (kickCooldowns[uuid] ?: 0L) < COOLDOWN_MS) return
        kickCooldowns[uuid] = now

        val reason = entry.reason
        val shortReason = reason.length <= 35

        localMsg(if (shortReason) "\u00a74[SL] \u00a7f$name \u00a7cis a shitter. \u00a77Reason: $reason" else "\u00a74[SL] \u00a7f$name \u00a7cis a shitter.")

        val delay = kickDelay.value.toLong()
        if (partyWarning.value) {
            Thread.sleep((delay * 8 / 11).coerceAtLeast(100))
            mc.execute { mc.player?.connection?.sendCommand(if (shortReason) "pc $name is a shitter. Reason: $reason" else "pc $name is a shitter.") }
            Thread.sleep((delay * 3 / 11).coerceAtLeast(50))
        } else Thread.sleep(delay)
        mc.execute { mc.player?.connection?.sendCommand("p kick $name") }
    }

    // ==================== Commands ====================

    private const val PAGE_SIZE = 10

    private fun showHelp(source: FabricClientCommandSource) {
        source.sendFeedback(Component.literal("\u00a74[SL] \u00a7fShitterList \u00a77- original mod by \u00a7fqwertycry \u00a78(github.com/qwertycry/Shitterlist)"))
        source.sendFeedback(Component.literal("\u00a7f/sl add <nick> [reason] \u00a77- add (alias: /sla)"))
        source.sendFeedback(Component.literal("\u00a7f/sl remove <nick> \u00a77- remove (alias: /slr)"))
        source.sendFeedback(Component.literal("\u00a7f/sl list [page] \u00a77- list 10/page (alias: /sll)"))
        source.sendFeedback(Component.literal("\u00a7f/sl parse \u00a77- resolve all Unknown nicknames"))
        source.sendFeedback(Component.literal("\u00a7f/sl help \u00a77- this help"))
    }

    private fun showList(source: FabricClientCommandSource, page: Int) {
        if (entries.isEmpty()) return source.sendFeedback(Component.literal("\u00a74[SL] \u00a77List is empty."))
        val totalPages = (entries.size + PAGE_SIZE - 1) / PAGE_SIZE
        val p = page.coerceIn(1, totalPages)
        val from = (p - 1) * PAGE_SIZE
        val to = minOf(from + PAGE_SIZE, entries.size)
        source.sendFeedback(Component.literal("\u00a74[SL] \u00a77Page \u00a7f$p\u00a77/\u00a7f$totalPages \u00a78(total: ${entries.size})"))
        entries.subList(from, to).forEachIndexed { i, entry ->
            val name = if (entry.nickname == "Unknown") "\u00a78${entry.uuid}" else "\u00a7f${entry.nickname}"
            source.sendFeedback(Component.literal("\u00a77${from + i + 1}. $name \u00a78(reason: ${entry.reason})"))
        }
        if (p < totalPages) source.sendFeedback(Component.literal("\u00a77Next: \u00a7f/sl list ${p + 1}"))
    }

    private fun handleAdd(source: FabricClientCommandSource, nickname: String, reason: String): Int {
        if (! nickname.matches(NICK_REGEX)) {
            source.sendError(Component.literal("\u00a7c[SL] Invalid nickname: \u00a7f$nickname")); return 0
        }
        Thread {
            runCatching {
                val profile = fetchProfile(nickname)
                    ?: return@Thread source.sendFeedback(Component.literal("\u00a7c[SL] Player \u00a7f$nickname \u00a7cnot found."))
                val (uuid, name) = profile
                val exists = getEntry(uuid) != null
                updateEntry(uuid, name, reason)
                if (exists) source.sendFeedback(Component.literal("\u00a7e[SL] \u00a7f$name \u00a7eupdated. \u00a78Reason: $reason"))
                else source.sendFeedback(Component.literal("\u00a7a[SL] \u00a7f$name \u00a7aadded. \u00a78Reason: $reason"))
            }.onFailure { source.sendFeedback(Component.literal("\u00a7c[SL] Error: ${it.message}")) }
        }.also { it.isDaemon = true; it.name = "sl-add" }.start()
        return 1
    }

    private fun handleRemove(source: FabricClientCommandSource, nickname: String): Int {
        if (! nickname.matches(NICK_REGEX)) {
            source.sendError(Component.literal("\u00a7c[SL] Invalid nickname: \u00a7f$nickname")); return 0
        }
        Thread {
            runCatching {
                val profile = fetchProfile(nickname)
                    ?: return@Thread source.sendFeedback(Component.literal("\u00a7c[SL] Player \u00a7f$nickname \u00a7cnot found."))
                val (uuid, name) = profile
                val removed = entries.removeIf { normalise(it.uuid) == normalise(uuid) }
                if (removed) { save(); source.sendFeedback(Component.literal("\u00a7a[SL] \u00a7f$name \u00a7aremoved.")) }
                else source.sendFeedback(Component.literal("\u00a7e[SL] \u00a7f$name \u00a7ewas not on the list."))
            }.onFailure { source.sendFeedback(Component.literal("\u00a7c[SL] Error: ${it.message}")) }
        }.also { it.isDaemon = true; it.name = "sl-remove" }.start()
        return 1
    }

    private fun handleParse(source: FabricClientCommandSource) {
        val unknowns = entries.filter { it.nickname == "Unknown" }
        if (unknowns.isEmpty()) return source.sendFeedback(Component.literal("\u00a7a[SL] All nicknames already resolved!"))
        source.sendFeedback(Component.literal("\u00a77[SL] Parsing ${unknowns.size} players... (1.5s delay)"))
        Thread {
            var resolved = 0
            var failed = 0
            unknowns.forEachIndexed { i, entry ->
                runCatching {
                    val name = fetchNameByUuid(entry.uuid)
                    if (name != null) {
                        val idx = entries.indexOfFirst { normalise(it.uuid) == normalise(entry.uuid) }
                        if (idx != - 1) { entries[idx] = entries[idx].copy(nickname = name); save() }
                        resolved ++
                        source.sendFeedback(Component.literal("\u00a77[${i + 1}/${unknowns.size}] \u00a7a${entry.uuid} -> \u00a7f$name"))
                    } else {
                        failed ++
                        source.sendFeedback(Component.literal("\u00a77[${i + 1}/${unknowns.size}] \u00a7c${entry.uuid} -> not found"))
                    }
                }.onFailure { failed ++ }
                if (i < unknowns.size - 1) Thread.sleep(1500)
            }
            source.sendFeedback(Component.literal("\u00a74[SL] \u00a7aDone! Resolved: \u00a7f$resolved \u00a7a| Not found: \u00a7f$failed"))
        }.also { it.isDaemon = true; it.name = "sl-parse" }.start()
    }

    // ==================== Init ====================

    override fun init() {
        load()

        ClientReceiveMessageEvents.ALLOW_GAME.register { message, _ ->
            if (enabled) runCatching {
                handleMessage(STRIP_PATTERN.matcher(message.string).replaceAll(""))
            }
            true
        }

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            fun buildTree(root: LiteralArgumentBuilder<FabricClientCommandSource>) = root
                .executes { ctx -> showHelp(ctx.source); 1 }
                .then(literal("add")
                    .then(argument("nickname", StringArgumentType.word())
                        .executes { ctx -> handleAdd(ctx.source, StringArgumentType.getString(ctx, "nickname"), "1") }
                        .then(argument("reason", StringArgumentType.greedyString())
                            .executes { ctx -> handleAdd(ctx.source, StringArgumentType.getString(ctx, "nickname"), StringArgumentType.getString(ctx, "reason")) })))
                .then(literal("remove")
                    .then(argument("nickname", StringArgumentType.word())
                        .executes { ctx -> handleRemove(ctx.source, StringArgumentType.getString(ctx, "nickname")) }))
                .then(literal("list")
                    .executes { ctx -> showList(ctx.source, 1); 1 }
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes { ctx -> showList(ctx.source, IntegerArgumentType.getInteger(ctx, "page")); 1 }))
                .then(literal("parse").executes { ctx -> handleParse(ctx.source); 1 })
                .then(literal("help").executes { ctx -> showHelp(ctx.source); 1 })

            dispatcher.register(buildTree(literal("shitter")))
            dispatcher.register(buildTree(literal("sl")))

            dispatcher.register(literal("sla")
                .executes { ctx -> showHelp(ctx.source); 1 }
                .then(argument("nickname", StringArgumentType.word())
                    .executes { ctx -> handleAdd(ctx.source, StringArgumentType.getString(ctx, "nickname"), "1") }
                    .then(argument("reason", StringArgumentType.greedyString())
                        .executes { ctx -> handleAdd(ctx.source, StringArgumentType.getString(ctx, "nickname"), StringArgumentType.getString(ctx, "reason")) })))

            dispatcher.register(literal("slr")
                .executes { ctx -> showHelp(ctx.source); 1 }
                .then(argument("nickname", StringArgumentType.word())
                    .executes { ctx -> handleRemove(ctx.source, StringArgumentType.getString(ctx, "nickname")) }))

            dispatcher.register(literal("sll")
                .executes { ctx -> showList(ctx.source, 1); 1 }
                .then(argument("page", IntegerArgumentType.integer(1))
                    .executes { ctx -> showList(ctx.source, IntegerArgumentType.getInteger(ctx, "page")); 1 }))
        }
    }
}
