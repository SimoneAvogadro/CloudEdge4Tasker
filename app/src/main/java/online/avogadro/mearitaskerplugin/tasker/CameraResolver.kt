package online.avogadro.mearitaskerplugin.tasker

import com.meari.sdk.bean.CameraInfo

object CameraResolver {
    /**
     * Resolves a camera selector to a list of matching cameras.
     *
     * Selector rules:
     * - null / "" / "*" → all cameras
     * - Purely numeric string → exact match on deviceID
     * - String containing "*" (but not just "*") → glob match on deviceName (case-insensitive)
     * - Non-numeric string without "*" → exact match on deviceName (case-insensitive)
     */
    @JvmStatic
    fun resolve(selector: String?, deviceList: List<CameraInfo>): List<CameraInfo> {
        if (selector.isNullOrEmpty() || selector == "*") {
            return deviceList.toList()
        }

        // Purely numeric → exact match on deviceID
        if (selector.all { it.isDigit() }) {
            return deviceList.filter { it.deviceID == selector }
        }

        // Contains wildcard → glob match on deviceName
        if (selector.contains("*")) {
            val regex = buildGlobRegex(selector)
            return deviceList.filter { regex.matches(it.deviceName ?: "") }
        }

        // Non-numeric, no wildcard → exact match on deviceName (case-insensitive)
        return deviceList.filter { it.deviceName.equals(selector, ignoreCase = true) }
    }

    private fun buildGlobRegex(glob: String): Regex {
        // Split on *, escape each segment for regex, rejoin with .*
        val pattern = glob.split("*").joinToString(".*") {
            java.util.regex.Pattern.quote(it)
        }
        return Regex("^$pattern$", RegexOption.IGNORE_CASE)
    }
}
