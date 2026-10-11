package top.xiaofeigun.tvliveplayer.util

object VersionComparator {

    /**
     * Cleans version string by removing leading 'v'/'V', whitespace, and build metadata.
     */
    fun cleanVersion(version: String): String {
        var v = version.trim()
        if (v.startsWith("v", ignoreCase = true)) {
            v = v.substring(1).trim()
        }
        val plusIdx = v.indexOf('+')
        if (plusIdx != -1) {
            v = v.substring(0, plusIdx).trim()
        }
        return v
    }

    /**
     * Determines whether [currentVersion] and [remoteVersion] are the same version.
     */
    fun isSameVersion(currentVersion: String, remoteVersion: String): Boolean {
        val cleanCurrent = cleanVersion(currentVersion)
        val cleanRemote = cleanVersion(remoteVersion)
        if (cleanRemote.isBlank() || cleanCurrent.isBlank()) return false
        return cleanRemote.equals(cleanCurrent, ignoreCase = true)
    }

    /**
     * Determines whether there is a version difference/update available.
     * As long as [remoteVersion] is non-empty and does not match [currentVersion],
     * an update is available (allows upgrading or rolling back to remote release).
     */
    fun hasUpdate(currentVersion: String, remoteVersion: String): Boolean {
        val cleanCurrent = cleanVersion(currentVersion)
        val cleanRemote = cleanVersion(remoteVersion)
        if (cleanRemote.isBlank()) return false
        return !cleanRemote.equals(cleanCurrent, ignoreCase = true)
    }

    /**
     * Compares [remoteVersion] against [currentVersion].
     * Returns:
     *  > 0 if remoteVersion is strictly newer than currentVersion
     *  < 0 if remoteVersion is older than currentVersion
     *  == 0 if versions match
     */
    fun compare(currentVersion: String, remoteVersion: String): Int {
        val cleanCurrent = cleanVersion(currentVersion)
        val cleanRemote = cleanVersion(remoteVersion)

        if (cleanRemote.equals(cleanCurrent, ignoreCase = true)) return 0
        if (cleanCurrent.isBlank()) return 1
        if (cleanRemote.isBlank()) return -1

        val currentParts = splitVersion(cleanCurrent)
        val remoteParts = splitVersion(cleanRemote)

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val currPart = currentParts.getOrNull(i) ?: "0"
            val remPart = remoteParts.getOrNull(i) ?: "0"

            val currNum = currPart.toLongOrNull()
            val remNum = remPart.toLongOrNull()

            if (currNum != null && remNum != null) {
                if (remNum != currNum) {
                    return remNum.compareTo(currNum)
                }
            } else {
                val cmp = remPart.compareTo(currPart, ignoreCase = true)
                if (cmp != 0) return cmp
            }
        }
        return 0
    }

    /**
     * Determines whether [remoteVersion] is strictly newer than [currentVersion].
     */
    fun isNewer(currentVersion: String, remoteVersion: String): Boolean {
        return compare(currentVersion, remoteVersion) > 0
    }

    private fun splitVersion(version: String): List<String> {
        return version.split(Regex("[.\\-_]")).filter { it.isNotBlank() }
    }
}
