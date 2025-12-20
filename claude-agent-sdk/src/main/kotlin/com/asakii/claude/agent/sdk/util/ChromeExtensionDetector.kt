package com.asakii.claude.agent.sdk.util

import mu.KotlinLogging
import java.io.File

/**
 * Detects Chrome extension installation status.
 *
 * This mirrors the official CLI's a4A() function logic:
 * 1. Find Chrome profile directories (Default, Profile 1, Profile 2, etc.)
 * 2. Check if the Claude extension ID exists in any profile's Extensions folder
 */
object ChromeExtensionDetector {
    private val logger = KotlinLogging.logger {}

    // Claude Chrome extension ID
    private const val CLAUDE_EXTENSION_ID = "fcoeoabgfenejglbffodgkkbkcdhcgfn"

    /**
     * Check if Claude Chrome extension is installed.
     *
     * @return true if extension is found in any Chrome profile
     */
    fun isExtensionInstalled(): Boolean {
        val chromeBasePath = getChromeBasePath() ?: run {
            logger.debug { "[ChromeExtensionDetector] Unsupported platform: ${getOsType()}" }
            return false
        }

        val baseDir = File(chromeBasePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            logger.debug { "[ChromeExtensionDetector] Chrome base path does not exist: $chromeBasePath" }
            return false
        }

        // Find Chrome profiles: Default, Profile 1, Profile 2, etc.
        val profiles = baseDir.listFiles { file ->
            file.isDirectory && (file.name == "Default" || file.name.startsWith("Profile "))
        } ?: emptyArray()

        logger.debug { "[ChromeExtensionDetector] Found Chrome profiles: ${profiles.map { it.name }}" }

        // Check each profile for the extension
        for (profile in profiles) {
            val extensionDir = File(profile, "Extensions/$CLAUDE_EXTENSION_ID")
            if (extensionDir.exists() && extensionDir.isDirectory) {
                logger.debug { "[ChromeExtensionDetector] Extension found in ${profile.name}" }
                return true
            }
        }

        logger.debug { "[ChromeExtensionDetector] Extension not found in any profile" }
        return false
    }

    /**
     * Get Chrome configuration base path for the current OS.
     */
    private fun getChromeBasePath(): String? {
        val homeDir = System.getProperty("user.home")

        return when (getOsType()) {
            OsType.MACOS -> "$homeDir/Library/Application Support/Google/Chrome"
            OsType.LINUX -> "$homeDir/.config/google-chrome"
            OsType.WINDOWS -> {
                val appData = System.getenv("LOCALAPPDATA")
                    ?: "$homeDir/AppData/Local"
                "$appData/Google/Chrome/User Data"
            }
            OsType.UNKNOWN -> null
        }
    }

    /**
     * Get the current operating system type.
     */
    private fun getOsType(): OsType {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") || osName.contains("darwin") -> OsType.MACOS
            osName.contains("linux") -> OsType.LINUX
            osName.contains("win") -> OsType.WINDOWS
            else -> OsType.UNKNOWN
        }
    }

    private enum class OsType {
        MACOS, LINUX, WINDOWS, UNKNOWN
    }
}
