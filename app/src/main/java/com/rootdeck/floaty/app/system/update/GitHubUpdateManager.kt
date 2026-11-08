package com.rootdeck.floaty.app.system.update

import android.content.Context
import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.rootdeck.floaty.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Checks the repository's latest public GitHub release and installs only an APK signed by this app's signer. */
class GitHubUpdateManager(private val activity: ComponentActivity) {

    data class UpdateInfo(
        val version: String,
        val downloadUrl: String,
        val assetName: String,
        val expectedSha256: String? = null
    )

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val owner = BuildConfig.GITHUB_REPOSITORY_OWNER.trim()
        val repo = BuildConfig.GITHUB_REPOSITORY_NAME.trim()
        require(owner.matches(Regex("^[A-Za-z0-9-]{1,39}$")) &&
            repo.matches(Regex("^[A-Za-z0-9._-]{1,100}$"))) {
            "The GitHub repository owner or name is not configured correctly."
        }

        val endpoint = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val json = requestJson(endpoint)
        val latestVersion = normalizeVersion(json.optString("tag_name"))
        require(numericVersionParts(latestVersion) != null) {
            "The latest GitHub release tag must contain a numeric version, such as v1.4.07."
        }

        val currentVersion = getInstalledVersionName()
        if (!isNewerVersion(latestVersion, currentVersion)) return@withContext null

        val assets = json.optJSONArray("assets")
            ?: throw IllegalStateException("The latest GitHub release has no assets.")
        var selected: UpdateInfo? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name").trim()
            val downloadUrl = asset.optString("browser_download_url").trim()
            if (name.endsWith(".apk", ignoreCase = true) && isTrustedReleaseDownloadUrl(downloadUrl)) {
                val digestValue = asset.optString("digest").trim()
                val digest = digestValue.takeIf {
                    it.startsWith("sha256:", ignoreCase = true) &&
                        it.substringAfter(':').matches(Regex("(?i)^[0-9a-f]{64}$"))
                }?.substringAfter(':')
                selected = UpdateInfo(latestVersion, downloadUrl, name, digest)
                break
            }
        }
        selected ?: throw IllegalStateException("Release $latestVersion has no downloadable APK asset.")
    }

    suspend fun downloadAndInstall(update: UpdateInfo) {
        val expectedVersion = normalizeVersion(update.version)
        require(numericVersionParts(expectedVersion) != null) {
            "The release version is not a valid numeric version."
        }
        val normalizedUpdate = update.copy(version = expectedVersion)
        val apk = withContext(Dispatchers.IO) { downloadAndValidate(normalizedUpdate) }
        val downloadedSha256 = withContext(Dispatchers.IO) { sha256(apk) }
        withContext(Dispatchers.Main) {
            installOrRequestPermission(apk, expectedVersion, downloadedSha256)
        }
    }

    /** Resume a previously verified install after Android's unknown-app install permission screen. */
    fun resumePendingInstall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) return

        val prefs = activity.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
        val expectedVersion = prefs.getString(KEY_PENDING_VERSION, null)
        val expectedSha256 = prefs.getString(KEY_PENDING_SHA256, null)
        if (expectedVersion.isNullOrBlank() || expectedSha256.isNullOrBlank()) return

        // Clear the hand-off before doing work so repeated onResume calls cannot start two installers.
        prefs.edit().remove(KEY_PENDING_VERSION).remove(KEY_PENDING_SHA256).apply()
        activity.lifecycleScope.launch {
            val apk = downloadedApkFile()
            val validationError = withContext(Dispatchers.IO) {
                try {
                    if (!apk.isFile || apk.length() <= 0L) {
                        throw IllegalStateException("The downloaded update file is missing.")
                    }
                    if (!sha256(apk).equals(expectedSha256, ignoreCase = true)) {
                        throw IllegalStateException("The downloaded APK changed before installation.")
                    }
                    validateApk(apk, expectedVersion)
                    null
                } catch (error: Exception) {
                    error
                }
            }
            if (validationError == null) {
                try {
                    launchInstaller(apk)
                } catch (error: Exception) {
                    apk.delete()
                    showInstallerError(error)
                }
            } else {
                apk.delete()
                showInstallerError(validationError)
            }
        }
    }

    private fun requestJson(url: URL): JSONObject {
        require(isTrustedApiUrl(url.toString())) { "The GitHub API URL is not trusted." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Floaty-Android-Updater")
            instanceFollowRedirects = false
        }
        try {
            val code = connection.responseCode
            if (code == 404) {
                throw IllegalStateException("No published release was found at https://github.com/$OWNER/$REPOSITORY/releases.")
            }
            if (code == 403 || code == 429) {
                throw IllegalStateException("GitHub rate-limited the update check. Please try again later.")
            }
            if (code !in 200..299) throw IllegalStateException("GitHub API returned HTTP $code.")
            if (connection.contentLength.toLong() > MAX_RELEASE_JSON_BYTES) {
                throw IllegalStateException("GitHub release metadata exceeds the size limit.")
            }
            val text = connection.inputStream.use { readBoundedUtf8(it, MAX_RELEASE_JSON_BYTES) }
            return JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBoundedUtf8(input: java.io.InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() > maxBytes - count) {
                throw IllegalStateException("The GitHub response exceeds the size limit.")
            }
            output.write(buffer, 0, count)
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    private fun downloadAndValidate(update: UpdateInfo): File {
        require(isTrustedReleaseDownloadUrl(update.downloadUrl)) {
            "The APK download URL is not a trusted GitHub release URL."
        }
        val directory = File(activity.filesDir, UPDATE_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Could not create the private update directory.")
        }
        if (!directory.isDirectory) throw IllegalStateException("The private update path is not a directory.")

        val target = downloadedApkFile()
        val partial = File(directory, "$UPDATE_APK_NAME.part")
        var promoted = false
        val connection = openTrustedReleaseConnection(URL(update.downloadUrl))
        try {
            if (!isTrustedReleaseDownloadUrl(connection.url.toString())) {
                throw IllegalStateException("The APK download redirected to an untrusted host.")
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("APK download failed (HTTP $code).")
            val declaredSize = connection.contentLength.toLong()
            if (declaredSize > MAX_APK_BYTES) throw IllegalStateException("The APK exceeds the 200 MiB size limit.")

            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IllegalStateException("The APK exceeds the 200 MiB size limit.")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    if (total == 0L || (declaredSize >= 0L && total != declaredSize)) {
                        throw IllegalStateException("The downloaded APK is empty or incomplete.")
                    }
                }
            }

            val actualDigest = sha256(partial)
            if (!update.expectedSha256.isNullOrBlank() &&
                !actualDigest.equals(update.expectedSha256, ignoreCase = true)) {
                throw IllegalStateException("The APK SHA-256 digest does not match the GitHub release metadata.")
            }
            if (target.exists() && !target.delete()) throw IllegalStateException("Could not replace the previous APK download.")
            if (!partial.renameTo(target)) throw IllegalStateException("Could not finalize the APK download.")
            promoted = true
            validateApk(target, update.version)
            return target
        } catch (error: Exception) {
            partial.delete()
            if (promoted) target.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openTrustedReleaseConnection(initialUrl: URL): HttpURLConnection {
        var current = initialUrl
        for (redirectCount in 0..MAX_RELEASE_REDIRECTS) {
            if (!isTrustedReleaseDownloadUrl(current.toString())) {
                throw IllegalStateException("The APK download redirected to an untrusted host.")
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "Floaty-Android-Updater")
                instanceFollowRedirects = false
            }
            val responseCode = try {
                connection.responseCode
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (redirectCount == MAX_RELEASE_REDIRECTS || location.isNullOrBlank()) {
                    throw IllegalStateException("The APK download returned too many or invalid redirects.")
                }
                val next = try {
                    URL(current, location)
                } catch (error: Exception) {
                    throw IllegalStateException("The APK redirect URL is invalid.", error)
                }
                if (!isTrustedReleaseDownloadUrl(next.toString())) {
                    throw IllegalStateException("The APK download redirected to an untrusted host.")
                }
                current = next
            } else {
                return connection
            }
        }
        throw IllegalStateException("The APK download returned too many redirects.")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02X".format(byte) }
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File, expectedVersion: String) {
        val archiveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES
        }
        val archive = activity.packageManager.getPackageArchiveInfo(file.absolutePath, archiveFlags)
            ?: throw IllegalStateException("The downloaded file is not a valid Android APK.")
        if (archive.packageName != activity.packageName) {
            throw IllegalStateException("The APK package name does not match this Floaty installation.")
        }
        val apkVersion = normalizeVersion(archive.versionName.orEmpty())
        if (apkVersion != expectedVersion) {
            throw IllegalStateException("The APK version ($apkVersion) does not match the GitHub release ($expectedVersion).")
        }
        val installedVersionCode = installedVersionCode()
        val downloadedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            archive.versionCode.toLong()
        }
        if (downloadedVersionCode <= installedVersionCode) {
            throw IllegalStateException("The release APK does not have a higher version code than the installed app.")
        }

        if (!hasSameOrDescendantSigningIdentity(archive)) {
            throw IllegalStateException("The APK is not signed by the installed Floaty signing certificate or its valid signing lineage.")
        }
    }

    @Suppress("DEPRECATION")
    private fun hasSameOrDescendantSigningIdentity(archive: PackageInfo): Boolean {
        val installedFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val installed = activity.packageManager.getPackageInfo(activity.packageName, installedFlags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val installedInfo = installed.signingInfo ?: return false
            val archiveInfo = archive.signingInfo ?: return false
            val installedCurrent = certificateDigests(installedInfo.apkContentsSigners)
            val archiveCurrent = certificateDigests(archiveInfo.apkContentsSigners)
            if (installedInfo.hasMultipleSigners() || archiveInfo.hasMultipleSigners()) {
                return installedInfo.hasMultipleSigners() && archiveInfo.hasMultipleSigners() &&
                    installedCurrent == archiveCurrent
            }
            val archiveHistory = certificateDigests(archiveInfo.signingCertificateHistory)
            return installedCurrent.size == 1 && installedCurrent.first() in archiveHistory
        }

        val installedSignatures = installed.signatures ?: emptyArray()
        val archiveSignatures = archive.signatures ?: emptyArray()
        return installedSignatures.isNotEmpty() &&
            certificateDigests(installedSignatures) == certificateDigests(archiveSignatures)
    }

    private fun certificateDigests(signatures: Array<Signature>): Set<String> = signatures.map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            .joinToString("") { byte -> "%02X".format(byte) }
    }.toSet()

    private fun installOrRequestPermission(file: File, version: String, fileSha256: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            activity.getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_PENDING_VERSION, version)
                .putString(KEY_PENDING_SHA256, fileSha256)
                .apply()
            val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(permissionIntent)
            return
        }
        if (!sha256(file).equals(fileSha256, ignoreCase = true)) {
            throw IllegalStateException("The APK changed after validation.")
        }
        validateApk(file, version)
        launchInstaller(file)
    }

    private fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
    }

    private fun showInstallerError(error: Exception) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "The update could not be prepared."
        android.app.AlertDialog.Builder(activity)
            .setTitle("Update installation failed")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun downloadedApkFile(): File = File(File(activity.filesDir, UPDATE_DIRECTORY), UPDATE_APK_NAME)

    private fun getInstalledVersionName(): String {
        @Suppress("DEPRECATION")
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return normalizeVersion(info.versionName.orEmpty())
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long {
        val info = activity.packageManager.getPackageInfo(activity.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    }

    private fun normalizeVersion(value: String): String = value.trim().removePrefix("v").removePrefix("V").trim()

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = numericVersionParts(latest) ?: return false
        val currentParts = numericVersionParts(current) ?: return false
        for (index in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(index) { 0 }
            val currentPart = currentParts.getOrElse(index) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private fun numericVersionParts(value: String): List<Int>? {
        val parts = value.split('.')
        if (parts.isEmpty() || parts.any { part -> part.isBlank() || part.any { !it.isDigit() } }) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    private fun isTrustedApiUrl(value: String): Boolean = try {
        val url = URL(value)
        url.protocol.equals("https", true) && url.userInfo == null && url.port == -1 &&
            url.host.equals("api.github.com", true) &&
            url.path == "/repos/$OWNER/$REPOSITORY/releases/latest" &&
            url.query == null && url.ref == null
    } catch (_: Exception) {
        false
    }

    private fun isTrustedReleaseDownloadUrl(value: String): Boolean = try {
        val url = URL(value)
        val host = url.host.lowercase()
        val repoReleasePath = url.path.startsWith("/$OWNER/$REPOSITORY/releases/download/")
        url.protocol.equals("https", true) && url.userInfo == null && url.port == -1 && url.ref == null &&
            when (host) {
                "github.com" -> repoReleasePath
                "objects.githubusercontent.com", "release-assets.githubusercontent.com" -> true
                else -> false
            }
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val OWNER = "Official-RootDeck"
        private const val REPOSITORY = "Floaty"
        private const val MAX_APK_BYTES = 200L * 1024L * 1024L
        private const val MAX_RELEASE_JSON_BYTES = 2 * 1024 * 1024
        private const val MAX_RELEASE_REDIRECTS = 5
        private const val UPDATE_DIRECTORY = "private-updates"
        private const val UPDATE_APK_NAME = "floaty-update.apk"
        private const val PENDING_PREFS = "floaty_update_pending"
        private const val KEY_PENDING_VERSION = "version"
        private const val KEY_PENDING_SHA256 = "sha256"
    }
}
