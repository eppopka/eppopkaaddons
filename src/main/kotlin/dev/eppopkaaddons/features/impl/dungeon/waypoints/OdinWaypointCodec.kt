package dev.eppopkaaddons.features.impl.dungeon.waypoints

import dev.eppopkaaddons.features.impl.dungeon.OdinDungeonWaypoints.Waypoint
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.core.BlockPos
import java.awt.Color
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.GZIPInputStream

object OdinWaypointCodec {
    val TYPE_NAMES = listOf("None", "Normal", "Secret", "Entrance", "Fairy Soul", "Mob", "Lever")
    private const val PREFIX = "EPAODIN1:"

    fun encode(data: Map<String, MutableList<Waypoint>>): String? = runCatching {
        val rooms = JsonObject()
        data.forEach { (room, waypoints) ->
            val array = JsonArray()
            waypoints.forEach { array.add(toJson(it)) }
            rooms.add(room, array)
        }
        PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(rooms.toString().toByteArray(StandardCharsets.UTF_8))
    }.getOrNull()

    fun decode(input: String): MutableMap<String, MutableList<Waypoint>>? = runCatching {
        val json = decodeText(input.trim())
        val root = JsonParser.parseString(json).asJsonObject
        val result = linkedMapOf<String, MutableList<Waypoint>>()
        root.entrySet().forEach { (room, value) ->
            result[room] = value.asJsonArray.mapNotNull { fromJson(it.asJsonObject) }.toMutableList()
        }
        result
    }.getOrNull()

    private fun decodeText(input: String): String {
        if (input.startsWith("{")) return input
        val encoded = input.removePrefix(PREFIX)
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }
            .recoverCatching { Base64.getDecoder().decode(encoded) }
            .getOrThrow()
        return runCatching {
            GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
        }.getOrElse { bytes.toString(StandardCharsets.UTF_8) }
    }

    private fun toJson(wp: Waypoint) = JsonObject().apply {
        add("blockPos", JsonObject().apply {
            addProperty("x", wp.pos.x)
            addProperty("y", wp.pos.y)
            addProperty("z", wp.pos.z)
        })
        addProperty("color", "#%02X%02X%02X%02X".format(wp.color.red, wp.color.green, wp.color.blue, wp.color.alpha))
        addProperty("filled", wp.filled)
        addProperty("depth", wp.depth)
        when (wp.type) {
            0 -> addProperty("type", "NONE")
            1 -> addProperty("type", "NORMAL")
            2 -> addProperty("type", "SECRET")
            3 -> addProperty("type", "ETHERWARP")
        }
        wp.title?.let { addProperty("title", it) }
        wp.box?.let { box ->
            add("aabb", JsonObject().apply {
                addProperty("minX", box.minX); addProperty("minY", box.minY); addProperty("minZ", box.minZ)
                addProperty("maxX", box.maxX); addProperty("maxY", box.maxY); addProperty("maxZ", box.maxZ)
            })
        }
    }

    private fun fromJson(obj: JsonObject): Waypoint? = runCatching {
        val posObject = obj.getAsJsonObject("blockPos")
        val pos = if (posObject != null) parseBlockPos(posObject)
        else BlockPos(obj["x"].asInt, obj["y"].asInt, obj["z"].asInt)
        val box = parseBox(obj["aabb"])
        val color = parseColor(obj["color"])
        val type = parseType(obj["type"], obj["secret"]?.asBoolean == true)
        Waypoint(
            pos = pos,
            color = color,
            filled = obj["filled"]?.asBoolean ?: false,
            depth = obj["depth"]?.asBoolean ?: false,
            type = type,
            title = obj["title"]?.asString?.ifBlank { null },
            box = box
        )
    }.getOrNull()

    private fun parseColor(element: JsonElement?): Color {
        if (element == null) return Color.GREEN
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) return Color(element.asInt, true)
        val raw = element.asString.removePrefix("#")
        if (raw.length != 6 && raw.length != 8) return Color.GREEN
        val value = raw.toLong(16)
        val red = ((value shr if (raw.length == 8) 24 else 16) and 0xFF).toInt()
        val green = ((value shr if (raw.length == 8) 16 else 8) and 0xFF).toInt()
        val blue = ((value shr if (raw.length == 8) 8 else 0) and 0xFF).toInt()
        val alpha = if (raw.length == 8) (value and 0xFF).toInt() else 255
        return Color(red, green, blue, alpha)
    }

    private fun parseType(element: JsonElement?, legacySecret: Boolean): Int {
        if (element == null) return if (legacySecret) 2 else 1
        if (element.isJsonPrimitive && element.asJsonPrimitive.isNumber) return element.asInt
        return when (element.asString.uppercase()) {
            "NONE" -> 0
            "NORMAL" -> 1
            "SECRET" -> 2
            "ETHERWARP", "ENTRANCE" -> 3
            else -> if (legacySecret) 2 else 1
        }
    }

    private fun parseBlockPos(obj: JsonObject): BlockPos = when {
        obj.has("x") -> BlockPos(obj["x"].asInt, obj["y"].asInt, obj["z"].asInt)
        obj.has("field_11173") -> BlockPos(obj["field_11175"].asInt, obj["field_11174"].asInt, obj["field_11173"].asInt)
        else -> BlockPos.ZERO
    }

    private fun parseBox(element: JsonElement?): Waypoint.BoxData? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonArray) {
            val values = element.asJsonArray
            if (values.size() < 6) return null
            return Waypoint.BoxData(
                values[0].asDouble, values[1].asDouble, values[2].asDouble,
                values[3].asDouble, values[4].asDouble, values[5].asDouble,
            )
        }
        val obj = element.asJsonObject
        return when {
            obj.has("minX") -> Waypoint.BoxData(
                obj["minX"].asDouble, obj["minY"].asDouble, obj["minZ"].asDouble,
                obj["maxX"].asDouble, obj["maxY"].asDouble, obj["maxZ"].asDouble,
            )
            obj.has("field_1323") -> Waypoint.BoxData(
                obj["field_1323"].asDouble, obj["field_1322"].asDouble, obj["field_1321"].asDouble,
                obj["field_1320"].asDouble, obj["field_1325"].asDouble, obj["field_1324"].asDouble,
            )
            obj.has("field_72340_a") -> Waypoint.BoxData(
                obj["field_72340_a"].asDouble, obj["field_72338_b"].asDouble, obj["field_72339_c"].asDouble,
                obj["field_72336_d"].asDouble, obj["field_72337_e"].asDouble, obj["field_72334_f"].asDouble,
            )
            else -> null
        }
    }
}