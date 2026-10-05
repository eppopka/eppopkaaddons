package dev.eppopkaaddons.init

import dev.eppopkaaddons.EppopkaAddons
import dev.eppopkaaddons.utils.GsonUtils
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.io.*
import java.net.URI
import java.nio.file.*
import java.util.zip.*
import javax.net.ssl.HttpsURLConnection
import kotlin.io.path.*

object DataDownloader {
    private const val DOWNLOAD_URL = "https://api.noamm.org/na/data/download"
    private const val HASH_URL = "https://api.noamm.org/na/data/version"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val RESOURCE_ROOT = "/assets/eppopkaaddons/data"
    private val LOGGER = LoggerFactory.getLogger("${EppopkaAddons.MOD_NAME}-DataDownloader")
    private val bundledFiles = setOf(
        "blacklistDrops.json",
        "boulderSolutions.json",
        "creeperBeamSolutions.json",
        "enchants.json",
        "features.json",
        "iHateDioriteBlocks.json",
        "quizSolutions.json",
        "rooms-modern.json",
        "rooms.json",
        "runSplits.json",
        "uselessMessages.json",
        "warpShortcuts.json",
        "watcherMobsNames.json",
        "waterSolutions.json",
    )

    val modDataPath: Path = FabricLoader.getInstance().configDir
        .resolve(EppopkaAddons.MOD_NAME)
        .resolve("data")
        .also { path ->
            migrateLegacyData(path)
            path.createDirectories()
            seedBundledData(path)
        }

    /**
     * Refreshes the bundled fallback data when the network is available. Startup remains safe
     * offline: every statically initialized consumer can already read the local fallback.
     */
    @JvmStatic
    fun prepareData() {
        runCatching(::downloadData).onFailure {
            LOGGER.warn("Remote data update unavailable; using bundled fallback data.", it)
        }
        // A failed or interrupted update must never leave startup without the minimum dataset.
        modDataPath.createDirectories()
        seedBundledData(modDataPath)
    }

    @JvmStatic
    fun downloadData() {
        val versionFile = modDataPath.resolve("version.txt")

        LOGGER.info("Checking for remote data updates...")

        val connection = URI.create(HASH_URL).toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "EppopkaAddons-DataDownloader")

        val remoteHash = connection.inputStream.bufferedReader().use(BufferedReader::readText)
        val localHash = if (versionFile.exists()) versionFile.readText().trim() else null

        if (remoteHash != localHash || ! modDataPath.exists()) {
            LOGGER.info("Update required. Remote: $remoteHash, Local: ${localHash ?: "None"}")
            update(versionFile, remoteHash)
        }
        else LOGGER.info("Data is up-to-date (Version: $localHash).")
    }

    private fun update(versionFile: Path, newHash: String) = runCatching {
        val tempZipFile = Files.createTempFile("data-download-", ".zip")
        val connection = URI.create(DOWNLOAD_URL).toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("User-Agent", "EppopkaAddons-DataDownloader")

        connection.inputStream.use { input ->
            Files.copy(input, tempZipFile, StandardCopyOption.REPLACE_EXISTING)
        }

        if (modDataPath.exists()) modDataPath.toFile().deleteRecursively()
        modDataPath.createDirectories()

        unzip(tempZipFile)

        versionFile.writeText(newHash)
        tempZipFile.deleteIfExists()

        LOGGER.info("Data update successful.")
    }.onFailure {
        LOGGER.error("Error while updating ${EppopkaAddons.MOD_NAME}-data", it)
    }

    private fun unzip(zipFilePath: Path) = ZipInputStream(zipFilePath.inputStream()).use { zis ->
        val root = modDataPath.toAbsolutePath().normalize()
        var rootDirName: String? = null

        while (true) {
            val entry = zis.nextEntry ?: break

            if (rootDirName == null) rootDirName = entry.name.substringBefore('/') + "/"

            val entryName = entry.name.removePrefix(rootDirName).ifEmpty { continue }
            val targetPath = root.resolve(entryName).normalize()

            if (! targetPath.startsWith(root)) throw IOException("Blocked path traversal entry: ${entry.name}")

            if (entry.isDirectory) targetPath.createDirectories()
            else {
                targetPath.parent?.createDirectories()
                Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }

            zis.closeEntry()
        }
    }

    inline fun <reified T: Any> loadJson(fileName: String): T {
        val file = modDataPath.resolve(fileName)
        require(file.exists()) { "Required data file is unavailable: $fileName" }
        return GsonUtils.decode(file.readText())
    }

    private fun seedBundledData(targetDirectory: Path) {
        bundledFiles.forEach { fileName ->
            val target = targetDirectory.resolve(fileName)
            if (target.exists()) return@forEach

            val resource = DataDownloader::class.java.getResourceAsStream("$RESOURCE_ROOT/$fileName")
            if (resource == null) {
                LOGGER.error("Bundled fallback data is missing: $fileName")
                return@forEach
            }

            resource.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    private fun migrateLegacyData(targetDirectory: Path) {
        if (targetDirectory.exists()) return
        val legacyDirectory = FabricLoader.getInstance().configDir.resolve("EppopkaAddons").resolve("data")
        if (!legacyDirectory.exists() || legacyDirectory == targetDirectory) return

        runCatching {
            legacyDirectory.toFile().copyRecursively(targetDirectory.toFile(), overwrite = false)
            LOGGER.info("Migrated legacy data directory to $targetDirectory")
        }.onFailure {
            LOGGER.warn("Could not migrate the legacy data directory; bundled data will be used.", it)
        }
    }
}